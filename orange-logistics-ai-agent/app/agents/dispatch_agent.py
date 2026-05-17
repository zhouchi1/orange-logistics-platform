"""智能调度 Agent

基于 LangChain 实现的智能调度决策 Agent。
能力：感知当前状态、分析约束、生成调度方案、调用优化服务。
"""
from typing import Dict, List, Optional, Any
from langchain.agents import AgentExecutor
from langchain_openai import ChatOpenAI
from langchain.agents.format_scratchpad.openai_tools import format_to_openai_tool_messages
from langchain.agents.output_parsers.openai_tools import OpenAIToolsAgentOutputParser
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
import logging

from app.config import settings
from app.prompts.dispatch_prompt import DISPATCH_SYSTEM_PROMPT
from app.tools.prediction_tool import PredictionTool
from app.tools.optimization_tool import OptimizationTool
from app.tools.database_tool import DatabaseTool
from app.tools.tracking_tool import TrackingTool
from app.tools.notification_tool import NotificationTool

logger = logging.getLogger(__name__)


class DispatchAgent:
    """智能调度 Agent

    职责：
    1. 感知当前配送状态（在途车辆、待配送订单、门店需求）
    2. 分析约束条件（车辆容量、时间窗口、司机工时）
    3. 生成调度方案（调用预测和优化服务）
    4. 执行调度决策（通知司机、更新系统）
    """

    def __init__(self):
        self.llm = ChatOpenAI(
            model=settings.LLM_MODEL,
            temperature=settings.LLM_TEMPERATURE,
            max_tokens=settings.LLM_MAX_TOKENS,
            openai_api_key=settings.OPENAI_API_KEY,
            openai_api_base=settings.OPENAI_API_BASE,
        )

        # 注册工具
        self.tools = [
            PredictionTool(),
            OptimizationTool(),
            DatabaseTool(),
            TrackingTool(),
            NotificationTool(),
        ]

        # 构建 Agent
        self.agent_executor = self._build_agent()

    def _build_agent(self) -> AgentExecutor:
        """构建 Agent 执行器"""
        prompt = ChatPromptTemplate.from_messages([
            ("system", DISPATCH_SYSTEM_PROMPT),
            MessagesPlaceholder(variable_name="chat_history", optional=True),
            ("human", "{input}"),
            MessagesPlaceholder(variable_name="agent_scratchpad"),
        ])

        # 绑定工具到 LLM
        llm_with_tools = self.llm.bind_tools(self.tools)

        # 构建 Agent
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
            max_iterations=10,
            handle_parsing_errors=True,
        )

    async def dispatch(
        self,
        request: Dict[str, Any],
        chat_history: Optional[List] = None,
    ) -> Dict[str, Any]:
        """执行调度决策

        Args:
            request: 调度请求
            chat_history: 对话历史

        Returns:
            调度决策结果
        """
        # 构建输入
        input_text = self._format_dispatch_request(request)

        try:
            result = await self.agent_executor.ainvoke({
                "input": input_text,
                "chat_history": chat_history or [],
            })

            return {
                "status": "success",
                "decision": result["output"],
                "request_type": request.get("type", "general"),
                "urgency": request.get("urgency", "normal"),
            }
        except Exception as e:
            logger.error(f"Dispatch agent error: {e}")
            return {
                "status": "error",
                "error": str(e),
                "fallback": "建议人工介入处理",
            }

    def _format_dispatch_request(self, request: Dict) -> str:
        """格式化调度请求为自然语言"""
        req_type = request.get("type", "general")

        if req_type == "new_orders":
            orders = request.get("orders", [])
            return (
                f"有 {len(orders)} 个新订单需要调度。\n"
                f"订单详情：{orders}\n"
                f"当前可用车辆：{request.get('available_vehicles', '未知')}\n"
                f"请分析约束条件并生成最优调度方案。"
            )
        elif req_type == "rebalance":
            return (
                f"需要进行运力再平衡。\n"
                f"当前状态：{request.get('current_state', {})}\n"
                f"原因：{request.get('reason', '未知')}\n"
                f"请评估当前情况并给出调整建议。"
            )
        elif req_type == "urgent":
            return (
                f"紧急调度请求！\n"
                f"紧急原因：{request.get('reason', '未知')}\n"
                f"影响范围：{request.get('affected_stores', [])}\n"
                f"请立即分析并给出应急方案。"
            )
        else:
            return request.get("message", "请分析当前配送状态并给出调度建议。")
