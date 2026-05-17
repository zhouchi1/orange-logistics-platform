"""LangGraph 智能调度决策工作流

节点：感知（获取当前状态）→ 分析（评估约束）→ 决策（生成方案）→ 执行（调用优化服务）
条件边：根据紧急程度走不同路径
"""
from typing import Dict, List, Any, TypedDict, Annotated, Literal
from langgraph.graph import StateGraph, END
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
import logging
import json
import operator

from app.config import settings
from app.tools.prediction_tool import PredictionTool
from app.tools.optimization_tool import OptimizationTool
from app.tools.database_tool import DatabaseTool
from app.tools.notification_tool import NotificationTool

logger = logging.getLogger(__name__)


class DispatchState(TypedDict):
    """调度工作流状态"""
    # 输入
    request: Dict[str, Any]
    urgency: str  # low / normal / high / critical

    # 中间状态
    current_status: Dict[str, Any]
    constraints: Dict[str, Any]
    predictions: Dict[str, Any]
    optimization_result: Dict[str, Any]

    # 输出
    decision: str
    actions_taken: Annotated[List[str], operator.add]
    final_plan: Dict[str, Any]
    needs_human_approval: bool


class DispatchGraph:
    """LangGraph 调度决策工作流

    工作流程：
    1. perceive: 感知当前配送状态
    2. analyze: 分析约束条件和紧急程度
    3. predict: 调用预测服务获取时效预测
    4. decide: 根据分析结果生成调度方案
    5. execute: 执行调度决策（调用优化服务）
    6. notify: 通知相关人员
    """

    def __init__(self):
        self.llm = ChatOpenAI(
            model=settings.LLM_MODEL,
            temperature=0.1,
            openai_api_key=settings.OPENAI_API_KEY,
            openai_api_base=settings.OPENAI_API_BASE,
        )
        self.prediction_tool = PredictionTool()
        self.optimization_tool = OptimizationTool()
        self.database_tool = DatabaseTool()
        self.notification_tool = NotificationTool()

        self.graph = self._build_graph()

    def _build_graph(self) -> StateGraph:
        """构建 LangGraph 工作流"""
        workflow = StateGraph(DispatchState)

        # 添加节点
        workflow.add_node("perceive", self._perceive_node)
        workflow.add_node("analyze", self._analyze_node)
        workflow.add_node("predict", self._predict_node)
        workflow.add_node("decide", self._decide_node)
        workflow.add_node("execute", self._execute_node)
        workflow.add_node("notify", self._notify_node)
        workflow.add_node("escalate", self._escalate_node)

        # 设置入口
        workflow.set_entry_point("perceive")

        # 添加边
        workflow.add_edge("perceive", "analyze")

        # 条件边：根据紧急程度决定路径
        workflow.add_conditional_edges(
            "analyze",
            self._route_by_urgency,
            {
                "critical": "escalate",  # 紧急情况直接上报
                "normal": "predict",     # 正常流程走预测
            },
        )

        workflow.add_edge("predict", "decide")
        workflow.add_edge("decide", "execute")
        workflow.add_edge("execute", "notify")
        workflow.add_edge("notify", END)
        workflow.add_edge("escalate", END)

        return workflow.compile()

    async def _perceive_node(self, state: DispatchState) -> Dict:
        """感知节点：获取当前配送状态"""
        logger.info("Perceive: Getting current dispatch status")

        request = state["request"]

        # 查询当前状态
        try:
            # 获取在途车辆数
            vehicles_status = await self.database_tool._arun(
                query="查询当前在途车辆数量和状态"
            )
            # 获取待配送订单
            pending_orders = await self.database_tool._arun(
                query="查询待配送订单数量"
            )
        except Exception as e:
            vehicles_status = f"查询失败: {e}"
            pending_orders = "查询失败"

        current_status = {
            "vehicles": vehicles_status,
            "pending_orders": pending_orders,
            "request_type": request.get("type", "general"),
            "timestamp": request.get("timestamp", ""),
        }

        return {
            "current_status": current_status,
            "actions_taken": ["感知当前配送状态"],
        }

    async def _analyze_node(self, state: DispatchState) -> Dict:
        """分析节点：评估约束条件"""
        logger.info("Analyze: Evaluating constraints")

        request = state["request"]
        current_status = state.get("current_status", {})

        # 使用 LLM 分析约束
        analysis_prompt = f"""分析以下调度请求的约束条件和紧急程度：

请求类型：{request.get('type', 'general')}
当前状态：{json.dumps(current_status, ensure_ascii=False, default=str)}
请求详情：{json.dumps(request, ensure_ascii=False, default=str)}

请评估：
1. 紧急程度（low/normal/high/critical）
2. 主要约束条件
3. 可用资源

以 JSON 格式返回：{{"urgency": "...", "constraints": {{...}}, "available_resources": {{...}}}}"""

        response = await self.llm.ainvoke([HumanMessage(content=analysis_prompt)])

        # 解析响应
        try:
            analysis = json.loads(response.content)
        except json.JSONDecodeError:
            analysis = {
                "urgency": request.get("urgency", "normal"),
                "constraints": {"time_window": True, "capacity": True},
                "available_resources": {},
            }

        urgency = analysis.get("urgency", "normal")

        return {
            "urgency": urgency,
            "constraints": analysis.get("constraints", {}),
            "actions_taken": [f"分析完成，紧急程度: {urgency}"],
        }

    def _route_by_urgency(self, state: DispatchState) -> Literal["critical", "normal"]:
        """条件路由：根据紧急程度决定路径"""
        urgency = state.get("urgency", "normal")
        if urgency == "critical":
            return "critical"
        return "normal"

    async def _predict_node(self, state: DispatchState) -> Dict:
        """预测节点：调用预测服务"""
        logger.info("Predict: Calling prediction service")

        request = state["request"]
        stores = request.get("stores", [])

        try:
            prediction_result = await self.prediction_tool._arun(
                action="predict_delivery_time",
                params=json.dumps({"stores": stores}),
            )
        except Exception as e:
            prediction_result = f"预测服务调用失败: {e}"

        return {
            "predictions": {"result": prediction_result},
            "actions_taken": ["调用预测服务获取时效预测"],
        }

    async def _decide_node(self, state: DispatchState) -> Dict:
        """决策节点：生成调度方案"""
        logger.info("Decide: Generating dispatch plan")

        # 综合所有信息做决策
        decision_prompt = f"""基于以下信息生成调度方案：

当前状态：{json.dumps(state.get('current_status', {}), ensure_ascii=False, default=str)}
约束条件：{json.dumps(state.get('constraints', {}), ensure_ascii=False, default=str)}
预测结果：{json.dumps(state.get('predictions', {}), ensure_ascii=False, default=str)}
原始请求：{json.dumps(state['request'], ensure_ascii=False, default=str)}

请生成具体的调度方案，包括：
1. 车辆分配
2. 路线规划建议
3. 时间安排
4. 注意事项"""

        response = await self.llm.ainvoke([HumanMessage(content=decision_prompt)])

        return {
            "decision": response.content,
            "needs_human_approval": state.get("urgency") == "high",
            "actions_taken": ["生成调度方案"],
        }

    async def _execute_node(self, state: DispatchState) -> Dict:
        """执行节点：调用优化服务"""
        logger.info("Execute: Calling optimization service")

        request = state["request"]

        try:
            optimization_result = await self.optimization_tool._arun(
                action="optimize_route",
                params=json.dumps(request.get("optimization_params", {})),
            )
        except Exception as e:
            optimization_result = f"优化服务调用失败: {e}"

        return {
            "optimization_result": {"result": optimization_result},
            "final_plan": {
                "decision": state.get("decision", ""),
                "optimization": optimization_result,
            },
            "actions_taken": ["调用路径优化服务"],
        }

    async def _notify_node(self, state: DispatchState) -> Dict:
        """通知节点：通知相关人员"""
        logger.info("Notify: Sending notifications")

        decision = state.get("decision", "")

        try:
            await self.notification_tool._arun(
                action="send",
                target="dispatch_team",
                message=f"新调度方案已生成：{decision[:200]}",
            )
        except Exception as e:
            logger.warning(f"Notification failed: {e}")

        return {
            "actions_taken": ["通知调度团队"],
        }

    async def _escalate_node(self, state: DispatchState) -> Dict:
        """上报节点：紧急情况上报人工"""
        logger.info("Escalate: Critical situation, escalating to human")

        return {
            "decision": "紧急情况，已上报人工处理",
            "needs_human_approval": True,
            "final_plan": {
                "action": "escalate",
                "reason": "紧急程度为 critical",
                "request": state["request"],
            },
            "actions_taken": ["紧急上报人工处理"],
        }

    async def run(self, request: Dict[str, Any]) -> Dict[str, Any]:
        """运行调度工作流

        Args:
            request: 调度请求

        Returns:
            工作流执行结果
        """
        initial_state = {
            "request": request,
            "urgency": request.get("urgency", "normal"),
            "current_status": {},
            "constraints": {},
            "predictions": {},
            "optimization_result": {},
            "decision": "",
            "actions_taken": [],
            "final_plan": {},
            "needs_human_approval": False,
        }

        result = await self.graph.ainvoke(initial_state)

        return {
            "decision": result.get("decision", ""),
            "final_plan": result.get("final_plan", {}),
            "actions_taken": result.get("actions_taken", []),
            "needs_human_approval": result.get("needs_human_approval", False),
            "urgency": result.get("urgency", "normal"),
        }
