"""LangGraph 异常处理工作流

ReAct 模式循环：观察 → 思考 → 行动 → 决策
"""
from typing import Dict, List, Any, TypedDict, Annotated, Literal, Optional
from langgraph.graph import StateGraph, END
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage
import logging
import json
import operator

from app.config import settings
from app.tools.tracking_tool import TrackingTool
from app.tools.database_tool import DatabaseTool
from app.tools.notification_tool import NotificationTool

logger = logging.getLogger(__name__)


class AnomalyState(TypedDict):
    """异常处理状态"""
    # 输入
    anomaly: Dict[str, Any]

    # ReAct 循环状态
    observations: Annotated[List[str], operator.add]
    thoughts: Annotated[List[str], operator.add]
    actions_taken: Annotated[List[str], operator.add]
    iteration: int

    # 输出
    severity: str
    root_cause: str
    resolution: str
    auto_resolved: bool
    needs_escalation: bool


class AnomalyGraph:
    """LangGraph 异常处理工作流

    ReAct 模式：
    1. observe: 获取异常详情和相关信息
    2. think: 分析可能原因
    3. act: 查询历史案例、获取更多信息
    4. decide: 自动处理 or 上报人工
    """

    MAX_ITERATIONS = 3

    def __init__(self):
        self.llm = ChatOpenAI(
            model=settings.LLM_MODEL,
            temperature=0.1,
            openai_api_key=settings.OPENAI_API_KEY,
            openai_api_base=settings.OPENAI_API_BASE,
        )
        self.tracking_tool = TrackingTool()
        self.database_tool = DatabaseTool()
        self.notification_tool = NotificationTool()
        self.graph = self._build_graph()

    def _build_graph(self) -> StateGraph:
        """构建异常处理工作流"""
        workflow = StateGraph(AnomalyState)

        # 添加节点
        workflow.add_node("observe", self._observe_node)
        workflow.add_node("think", self._think_node)
        workflow.add_node("act", self._act_node)
        workflow.add_node("decide", self._decide_node)
        workflow.add_node("auto_resolve", self._auto_resolve_node)
        workflow.add_node("escalate", self._escalate_node)

        # 设置入口
        workflow.set_entry_point("observe")

        # 边
        workflow.add_edge("observe", "think")
        workflow.add_edge("think", "act")

        # 条件边：行动后决定是否继续循环
        workflow.add_conditional_edges(
            "act",
            self._should_continue,
            {
                "continue": "observe",  # 继续 ReAct 循环
                "decide": "decide",     # 信息足够，做决策
            },
        )

        # 决策后的条件边
        workflow.add_conditional_edges(
            "decide",
            self._route_decision,
            {
                "auto": "auto_resolve",
                "escalate": "escalate",
            },
        )

        workflow.add_edge("auto_resolve", END)
        workflow.add_edge("escalate", END)

        return workflow.compile()

    async def _observe_node(self, state: AnomalyState) -> Dict:
        """观察节点：获取异常详情"""
        anomaly = state["anomaly"]
        iteration = state.get("iteration", 0)

        observations = []

        if iteration == 0:
            # 首次观察：获取基本信息
            observations.append(
                f"异常类型: {anomaly.get('type', '未知')}, "
                f"发生时间: {anomaly.get('occurred_at', '未知')}, "
                f"影响: {anomaly.get('affected_count', 1)} 个订单"
            )

            # 查询相关订单状态
            order_ids = anomaly.get("order_ids", [])
            if order_ids:
                try:
                    tracking = await self.tracking_tool._arun(
                        order_id=order_ids[0]
                    )
                    observations.append(f"订单轨迹: {tracking}")
                except Exception as e:
                    observations.append(f"订单查询失败: {e}")
        else:
            # 后续观察：查询更多信息
            try:
                history = await self.database_tool._arun(
                    query=f"查询类似异常的历史处理记录，异常类型: {anomaly.get('type')}"
                )
                observations.append(f"历史案例: {history}")
            except Exception as e:
                observations.append(f"历史查询失败: {e}")

        return {
            "observations": observations,
            "iteration": iteration + 1,
        }

    async def _think_node(self, state: AnomalyState) -> Dict:
        """思考节点：分析原因"""
        anomaly = state["anomaly"]
        observations = state.get("observations", [])

        prompt = f"""基于以下观察信息，分析异常的可能原因：

异常信息：{json.dumps(anomaly, ensure_ascii=False, default=str)}
观察记录：{json.dumps(observations, ensure_ascii=False)}

请分析：
1. 最可能的根本原因
2. 影响范围评估
3. 是否需要更多信息
4. 初步处置建议

以简洁的方式回答。"""

        response = await self.llm.ainvoke([HumanMessage(content=prompt)])

        return {
            "thoughts": [response.content],
        }

    async def _act_node(self, state: AnomalyState) -> Dict:
        """行动节点：执行信息收集"""
        thoughts = state.get("thoughts", [])
        anomaly = state["anomaly"]

        actions = []

        # 根据思考结果决定行动
        latest_thought = thoughts[-1] if thoughts else ""

        if "需要更多信息" in latest_thought or "查询" in latest_thought:
            try:
                result = await self.database_tool._arun(
                    query=f"查询异常相关的详细数据: {anomaly.get('type')}"
                )
                actions.append(f"查询详细数据: {result}")
            except Exception as e:
                actions.append(f"查询失败: {e}")
        else:
            actions.append("信息收集完成，准备做决策")

        return {
            "actions_taken": actions,
        }

    def _should_continue(self, state: AnomalyState) -> Literal["continue", "decide"]:
        """判断是否继续 ReAct 循环"""
        iteration = state.get("iteration", 0)
        actions = state.get("actions_taken", [])

        # 最多循环 MAX_ITERATIONS 次
        if iteration >= self.MAX_ITERATIONS:
            return "decide"

        # 如果最后一个行动表示信息足够
        if actions and "信息收集完成" in actions[-1]:
            return "decide"

        return "continue"

    async def _decide_node(self, state: AnomalyState) -> Dict:
        """决策节点：确定处理方式"""
        anomaly = state["anomaly"]
        observations = state.get("observations", [])
        thoughts = state.get("thoughts", [])

        # 评估严重程度
        anomaly_type = anomaly.get("type", "unknown")
        duration = anomaly.get("duration_hours", 0)
        affected = anomaly.get("affected_count", 1)

        # 严重度评估
        if affected > 10 or duration > 6:
            severity = "high"
        elif duration > 2 or affected > 3:
            severity = "medium"
        else:
            severity = "low"

        # 决策
        prompt = f"""基于以下分析，决定处理方式：

异常类型: {anomaly_type}
严重程度: {severity}
持续时间: {duration} 小时
影响范围: {affected} 个订单
分析结论: {thoughts[-1] if thoughts else '无'}

自动处理条件：严重程度为 low 或 medium 且有明确处理方案
上报条件：严重程度为 high 或无法确定处理方案

请给出：
1. 根本原因（一句话）
2. 处理方案
3. 决策：auto（自动处理）或 escalate（上报）"""

        response = await self.llm.ainvoke([HumanMessage(content=prompt)])

        # 解析决策
        auto_resolve = severity == "low" or (
            severity == "medium" and "auto" in response.content.lower()
        )

        return {
            "severity": severity,
            "root_cause": response.content,
            "auto_resolved": auto_resolve,
            "needs_escalation": not auto_resolve,
        }

    def _route_decision(self, state: AnomalyState) -> Literal["auto", "escalate"]:
        """路由决策结果"""
        if state.get("auto_resolved", False):
            return "auto"
        return "escalate"

    async def _auto_resolve_node(self, state: AnomalyState) -> Dict:
        """自动处理节点"""
        anomaly = state["anomaly"]

        # 执行自动处理
        resolution_actions = []

        anomaly_type = anomaly.get("type", "")
        if anomaly_type == "delay":
            # 延迟：通知客户
            try:
                await self.notification_tool._arun(
                    action="send",
                    target="customer",
                    message=f"您的订单配送稍有延迟，预计将在2小时内送达，给您带来不便深表歉意。",
                )
                resolution_actions.append("已通知客户配送延迟")
            except Exception:
                resolution_actions.append("客户通知发送失败")

        elif anomaly_type == "address_issue":
            resolution_actions.append("已发起地址确认流程")

        else:
            resolution_actions.append(f"已记录异常并标记为自动处理: {anomaly_type}")

        return {
            "resolution": f"自动处理完成: {'; '.join(resolution_actions)}",
            "actions_taken": resolution_actions,
        }

    async def _escalate_node(self, state: AnomalyState) -> Dict:
        """上报节点"""
        anomaly = state["anomaly"]

        try:
            await self.notification_tool._arun(
                action="send",
                target="ops_team",
                message=f"异常上报: {anomaly.get('type')} - {state.get('root_cause', '原因待查')}",
            )
        except Exception as e:
            logger.warning(f"Escalation notification failed: {e}")

        return {
            "resolution": f"已上报运营团队处理。根因分析: {state.get('root_cause', '待查')}",
            "actions_taken": ["上报运营团队"],
        }

    async def run(self, anomaly: Dict[str, Any]) -> Dict[str, Any]:
        """运行异常处理工作流"""
        initial_state = {
            "anomaly": anomaly,
            "observations": [],
            "thoughts": [],
            "actions_taken": [],
            "iteration": 0,
            "severity": "unknown",
            "root_cause": "",
            "resolution": "",
            "auto_resolved": False,
            "needs_escalation": False,
        }

        result = await self.graph.ainvoke(initial_state)

        return {
            "anomaly_id": anomaly.get("anomaly_id", "unknown"),
            "severity": result.get("severity", "unknown"),
            "root_cause": result.get("root_cause", ""),
            "resolution": result.get("resolution", ""),
            "auto_resolved": result.get("auto_resolved", False),
            "actions_taken": result.get("actions_taken", []),
            "iterations": result.get("iteration", 0),
        }
