"""异常处理 Agent

基于 LangChain 实现的异常自动处理 Agent。
能力：异常检测、原因分析、历史案例匹配、自动处置。
"""
from typing import Dict, List, Optional, Any
from langchain_openai import ChatOpenAI
from langchain.agents import AgentExecutor
from langchain.agents.format_scratchpad.openai_tools import format_to_openai_tool_messages
from langchain.agents.output_parsers.openai_tools import OpenAIToolsAgentOutputParser
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
import logging

from app.config import settings
from app.tools.tracking_tool import TrackingTool
from app.tools.database_tool import DatabaseTool
from app.tools.prediction_tool import PredictionTool
from app.tools.notification_tool import NotificationTool

logger = logging.getLogger(__name__)

ANOMALY_SYSTEM_PROMPT = """你是橙子便利物流异常处理专家 Agent。你的职责是自动分析和处理物流异常事件。

## 异常类型
1. 配送超时：预计送达时间已过但未送达
2. 包裹丢失：长时间无物流更新
3. 地址异常：无法送达的地址
4. 车辆故障：配送车辆出现问题
5. 天气异常：极端天气影响配送
6. 客户拒收：客户拒绝签收
7. 货损：包裹在运输中损坏

## 处理流程（ReAct 模式）
1. 观察：获取异常详细信息
2. 思考：分析可能原因，评估影响范围
3. 行动：查询历史类似案例，获取更多信息
4. 决策：自动处理 or 上报人工

## 自动处理条件
- 配送超时 < 2小时：自动通知客户预计延迟时间
- 地址异常：自动联系客户确认地址
- 轻微货损：自动发起理赔流程

## 上报人工条件
- 配送超时 > 4小时
- 包裹丢失
- 严重货损
- 客户投诉升级
- 批量异常（同一区域多个异常）

请使用工具获取信息，分析异常原因，并给出处置方案。"""


class AnomalyAgent:
    """异常处理 Agent

    使用 ReAct 模式循环：
    - 观察：获取异常详情
    - 思考：分析可能原因
    - 行动：查询历史类似案例、推荐处置方案
    - 决策：自动处理 or 上报人工
    """

    # 异常严重等级
    SEVERITY_LEVELS = {
        "low": ["轻微延迟", "地址不详", "轻微货损"],
        "medium": ["配送超时", "客户拒收", "车辆故障"],
        "high": ["包裹丢失", "严重货损", "批量异常"],
        "critical": ["安全事故", "大面积瘫痪", "数据泄露"],
    }

    def __init__(self):
        self.llm = ChatOpenAI(
            model=settings.LLM_MODEL,
            temperature=0.1,
            max_tokens=settings.LLM_MAX_TOKENS,
            openai_api_key=settings.OPENAI_API_KEY,
            openai_api_base=settings.OPENAI_API_BASE,
        )

        self.tools = [
            TrackingTool(),
            DatabaseTool(),
            PredictionTool(),
            NotificationTool(),
        ]

        self.agent_executor = self._build_agent()

    def _build_agent(self) -> AgentExecutor:
        """构建异常处理 Agent"""
        prompt = ChatPromptTemplate.from_messages([
            ("system", ANOMALY_SYSTEM_PROMPT),
            MessagesPlaceholder(variable_name="chat_history", optional=True),
            ("human", "{input}"),
            MessagesPlaceholder(variable_name="agent_scratchpad"),
        ])

        llm_with_tools = self.llm.bind_tools(self.tools)

        agent = (
            {
                "input": lambda x: x["input"],
                "chat_history": lambda x: x.get("chat_history", []),
                "agent_scratchpad": lambda x: format_to_openai_tool_messages(
                    x["intermediate_steps"]
                ),
            }
            | prompt
            | llm_with_tools
            | OpenAIToolsAgentOutputParser()
        )

        return AgentExecutor(
            agent=agent,
            tools=self.tools,
            verbose=True,
            max_iterations=8,
            handle_parsing_errors=True,
        )

    async def handle_anomaly(
        self,
        anomaly: Dict[str, Any],
        chat_history: Optional[List] = None,
    ) -> Dict[str, Any]:
        """处理异常事件

        Args:
            anomaly: 异常信息
            chat_history: 历史上下文

        Returns:
            处理结果
        """
        # 评估严重等级
        severity = self._assess_severity(anomaly)

        # 构建输入
        input_text = self._format_anomaly(anomaly, severity)

        try:
            result = await self.agent_executor.ainvoke({
                "input": input_text,
                "chat_history": chat_history or [],
            })

            # 解析处理决策
            decision = self._parse_decision(result["output"], severity)

            return {
                "status": "success",
                "anomaly_id": anomaly.get("anomaly_id", "unknown"),
                "severity": severity,
                "analysis": result["output"],
                "decision": decision,
                "auto_resolved": decision["action"] != "escalate",
            }
        except Exception as e:
            logger.error(f"Anomaly agent error: {e}")
            return {
                "status": "error",
                "anomaly_id": anomaly.get("anomaly_id", "unknown"),
                "severity": severity,
                "error": str(e),
                "decision": {
                    "action": "escalate",
                    "reason": "Agent 处理异常，自动上报人工",
                },
                "auto_resolved": False,
            }

    def _assess_severity(self, anomaly: Dict) -> str:
        """评估异常严重等级"""
        anomaly_type = anomaly.get("type", "unknown")
        duration_hours = anomaly.get("duration_hours", 0)
        affected_count = anomaly.get("affected_count", 1)

        if affected_count > 10 or anomaly_type in ["safety", "system_down"]:
            return "critical"
        elif duration_hours > 4 or anomaly_type in ["package_lost", "severe_damage"]:
            return "high"
        elif duration_hours > 2 or anomaly_type in ["delay", "rejection", "vehicle_issue"]:
            return "medium"
        else:
            return "low"

    def _format_anomaly(self, anomaly: Dict, severity: str) -> str:
        """格式化异常信息"""
        return (
            f"## 异常事件报告\n\n"
            f"- 异常ID: {anomaly.get('anomaly_id', 'N/A')}\n"
            f"- 类型: {anomaly.get('type', '未知')}\n"
            f"- 严重等级: {severity}\n"
            f"- 发生时间: {anomaly.get('occurred_at', '未知')}\n"
            f"- 持续时间: {anomaly.get('duration_hours', 0)} 小时\n"
            f"- 影响范围: {anomaly.get('affected_count', 1)} 个订单/门店\n"
            f"- 描述: {anomaly.get('description', '无描述')}\n"
            f"- 相关订单: {anomaly.get('order_ids', [])}\n\n"
            f"请分析此异常并给出处置方案。"
        )

    def _parse_decision(self, output: str, severity: str) -> Dict:
        """解析处理决策"""
        # 高严重度强制上报
        if severity in ("high", "critical"):
            return {
                "action": "escalate",
                "reason": f"严重等级: {severity}，需要人工介入",
                "suggested_actions": output,
            }

        # 检查输出中是否建议自动处理
        auto_keywords = ["自动", "已处理", "已通知", "已发起"]
        if any(k in output for k in auto_keywords):
            return {
                "action": "auto_resolve",
                "resolution": output,
            }

        return {
            "action": "escalate",
            "reason": "无法确定自动处理方案",
            "suggested_actions": output,
        }
