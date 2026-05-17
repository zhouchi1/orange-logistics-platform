"""客服 Agent

基于 LangChain 实现的智能客服 Agent。
能力：意图识别、RAG 检索、回复生成、人工转接。
"""
from typing import Dict, List, Optional, Any
from langchain_openai import ChatOpenAI
from langchain.agents import AgentExecutor
from langchain.agents.format_scratchpad.openai_tools import format_to_openai_tool_messages
from langchain.agents.output_parsers.openai_tools import OpenAIToolsAgentOutputParser
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
import logging

from app.config import settings
from app.prompts.customer_prompt import CUSTOMER_SYSTEM_PROMPT
from app.tools.tracking_tool import TrackingTool
from app.tools.database_tool import DatabaseTool
from app.tools.notification_tool import NotificationTool

logger = logging.getLogger(__name__)


class CustomerAgent:
    """智能客服 Agent

    职责：
    1. 意图识别：查件/催件/改地址/投诉/其他
    2. RAG 检索：从知识库获取相关信息
    3. 回复生成：结合上下文生成回答
    4. 人工转接：复杂问题升级
    """

    # 支持的意图类型
    INTENT_TYPES = [
        "track_package",      # 查件
        "urge_delivery",      # 催件
        "change_address",     # 改地址
        "complaint",          # 投诉
        "delivery_time",      # 查询配送时间
        "return_exchange",    # 退换货
        "general_inquiry",    # 一般咨询
        "human_transfer",     # 转人工
    ]

    def __init__(self):
        self.llm = ChatOpenAI(
            model=settings.LLM_MODEL,
            temperature=0.3,
            max_tokens=settings.LLM_MAX_TOKENS,
            openai_api_key=settings.OPENAI_API_KEY,
            openai_api_base=settings.OPENAI_API_BASE,
        )

        self.tools = [
            TrackingTool(),
            DatabaseTool(),
            NotificationTool(),
        ]

        self.agent_executor = self._build_agent()

    def _build_agent(self) -> AgentExecutor:
        """构建客服 Agent"""
        prompt = ChatPromptTemplate.from_messages([
            ("system", CUSTOMER_SYSTEM_PROMPT),
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
            max_iterations=5,
            handle_parsing_errors=True,
        )

    async def chat(
        self,
        message: str,
        session_id: str,
        chat_history: Optional[List] = None,
        user_info: Optional[Dict] = None,
    ) -> Dict[str, Any]:
        """处理客服对话

        Args:
            message: 用户消息
            session_id: 会话ID
            chat_history: 对话历史
            user_info: 用户信息

        Returns:
            回复结果
        """
        # 构建上下文
        context = ""
        if user_info:
            context = f"[用户信息] 姓名: {user_info.get('name', '未知')}, "
            context += f"手机: {user_info.get('phone', '未知')}\n"

        input_text = f"{context}{message}" if context else message

        try:
            result = await self.agent_executor.ainvoke({
                "input": input_text,
                "chat_history": chat_history or [],
            })

            response = result["output"]

            # 检查是否需要转人工
            needs_transfer = self._check_transfer_needed(response, message)

            return {
                "status": "success",
                "response": response,
                "session_id": session_id,
                "needs_human_transfer": needs_transfer,
                "intent": self._detect_intent(message),
            }
        except Exception as e:
            logger.error(f"Customer agent error: {e}")
            return {
                "status": "error",
                "response": "抱歉，系统暂时无法处理您的请求，正在为您转接人工客服。",
                "session_id": session_id,
                "needs_human_transfer": True,
                "error": str(e),
            }

    def _detect_intent(self, message: str) -> str:
        """简单意图检测"""
        keywords = {
            "track_package": ["查件", "在哪", "到哪了", "物流信息", "快递到哪"],
            "urge_delivery": ["催", "加急", "快点", "什么时候到", "太慢"],
            "change_address": ["改地址", "换地址", "修改地址", "改收货"],
            "complaint": ["投诉", "差评", "不满意", "态度差", "损坏"],
            "delivery_time": ["多久", "几天", "预计", "时效"],
            "return_exchange": ["退货", "换货", "退款", "退回"],
            "human_transfer": ["转人工", "人工客服", "找人"],
        }

        for intent, words in keywords.items():
            if any(w in message for w in words):
                return intent
        return "general_inquiry"

    def _check_transfer_needed(self, response: str, message: str) -> bool:
        """检查是否需要转人工"""
        transfer_signals = ["转人工", "人工客服", "无法处理", "升级"]
        if any(s in message for s in ["转人工", "人工客服", "找人"]):
            return True
        if any(s in response for s in transfer_signals):
            return True
        return False
