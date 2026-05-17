"""LangGraph 客服对话工作流
节点：意图识别 -> RAG 检索 -> 回复生成 -> 人工转接判断
"""
from typing import Dict, List, Any, TypedDict, Annotated, Literal, Optional
from langgraph.graph import StateGraph, END
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage, AIMessage
import logging
import json
import operator

from app.config import settings
from app.tools.tracking_tool import TrackingTool

logger = logging.getLogger(__name__)


class CustomerState(TypedDict):
    """客服对话状态"""
    # 输入
    message: str
    session_id: str
    user_info: Optional[Dict[str, Any]]
    chat_history: List[Any]

    # 中间状态
    intent: str
    intent_confidence: float
    retrieved_context: str
    tracking_info: Optional[Dict[str, Any]]

    # 输出
    response: str
    needs_human_transfer: bool
    actions_taken: Annotated[List[str], operator.add]


class CustomerGraph:
    """LangGraph 客服对话工作流

    工作流程：
    1. intent_recognition: 识别用户意图
    2. retrieve_context: RAG 检索相关知识
    3. track_package: 查询物流信息（条件）
    4. generate_response: 生成回复
    5. check_transfer: 判断是否需要转人工
    """

    def __init__(self):
        self.llm = ChatOpenAI(
            model=settings.LLM_MODEL,
            temperature=0.3,
            openai_api_key=settings.OPENAI_API_KEY,
            openai_api_base=settings.OPENAI_API_BASE,
        )
        self.tracking_tool = TrackingTool()
        self.graph = self._build_graph()

    def _build_graph(self) -> StateGraph:
        """构建客服对话工作流"""
        workflow = StateGraph(CustomerState)

        # 添加节点
        workflow.add_node("intent_recognition", self._intent_node)
        workflow.add_node("retrieve_context", self._retrieve_node)
        workflow.add_node("track_package", self._track_node)
        workflow.add_node("generate_response", self._generate_node)
        workflow.add_node("human_transfer", self._transfer_node)

        # 设置入口
        workflow.set_entry_point("intent_recognition")

        # 条件边：根据意图决定下一步
        workflow.add_conditional_edges(
            "intent_recognition",
            self._route_by_intent,
            {
                "tracking": "track_package",
                "knowledge": "retrieve_context",
                "transfer": "human_transfer",
            },
        )

        workflow.add_edge("track_package", "generate_response")
        workflow.add_edge("retrieve_context", "generate_response")
        workflow.add_edge("generate_response", END)
        workflow.add_edge("human_transfer", END)

        return workflow.compile()

    async def _intent_node(self, state: CustomerState) -> Dict:
        """意图识别节点"""
        message = state["message"]

        prompt = (
            '识别以下用户消息的意图，返回 JSON 格式：'
            '{"intent": "track_package|urge_delivery|change_address|complaint|'
            'delivery_time|return_exchange|general_inquiry|human_transfer", '
            '"confidence": 0.0-1.0}\n\n'
            f'用户消息：{message}'
        )

        response = await self.llm.ainvoke([HumanMessage(content=prompt)])

        try:
            result = json.loads(response.content)
            intent = result.get("intent", "general_inquiry")
            confidence = result.get("confidence", 0.5)
        except json.JSONDecodeError:
            intent = "general_inquiry"
            confidence = 0.5

        return {
            "intent": intent,
            "intent_confidence": confidence,
            "actions_taken": [f"意图识别: {intent} (置信度: {confidence:.2f})"],
        }

    def _route_by_intent(self, state: CustomerState) -> Literal["tracking", "knowledge", "transfer"]:
        """根据意图路由"""
        intent = state.get("intent", "general_inquiry")

        if intent in ("track_package", "urge_delivery", "delivery_time"):
            return "tracking"
        elif intent == "human_transfer" or state.get("intent_confidence", 0) < 0.3:
            return "transfer"
        else:
            return "knowledge"

    async def _track_node(self, state: CustomerState) -> Dict:
        """物流查询节点"""
        message = state["message"]

        try:
            tracking_result = await self.tracking_tool._arun(
                order_id=self._extract_order_id(message)
            )
        except Exception as e:
            tracking_result = f"查询失败: {e}"

        return {
            "tracking_info": {"result": tracking_result},
            "actions_taken": ["查询物流轨迹"],
        }

    async def _retrieve_node(self, state: CustomerState) -> Dict:
        """RAG 检索节点"""
        message = state["message"]

        # 简化版 RAG 检索（实际环境连接向量库）
        retrieved_text = ""
        logger.info(f"RAG retrieval for: {message[:50]}")

        return {
            "retrieved_context": retrieved_text,
            "actions_taken": ["检索知识库"],
        }

    async def _generate_node(self, state: CustomerState) -> Dict:
        """回复生成节点"""
        message = state["message"]
        intent = state.get("intent", "general_inquiry")
        context = state.get("retrieved_context", "")
        tracking = state.get("tracking_info", {})

        prompt = (
            f"你是橙子便利物流客服助手。请根据以下信息回复用户：\n"
            f"用户意图：{intent}\n"
            f"用户消息：{message}\n"
            f"物流信息：{json.dumps(tracking, ensure_ascii=False, default=str) if tracking else '无'}\n"
            f"知识库参考：{context if context else '无'}\n\n"
            f"要求：\n"
            f"- 语气友好专业\n"
            f"- 信息准确具体\n"
            f"- 如果无法解决，建议转人工\n"
            f"- 回复简洁，不超过200字"
        )

        response = await self.llm.ainvoke([
            HumanMessage(content=prompt)
        ])

        # 判断是否需要转人工
        needs_transfer = any(
            kw in response.content for kw in ["转人工", "无法处理", "建议联系"]
        )

        return {
            "response": response.content,
            "needs_human_transfer": needs_transfer,
            "actions_taken": ["生成回复"],
        }

    async def _transfer_node(self, state: CustomerState) -> Dict:
        """人工转接节点"""
        return {
            "response": "正在为您转接人工客服，请稍候...",
            "needs_human_transfer": True,
            "actions_taken": ["转接人工客服"],
        }

    def _extract_order_id(self, message: str) -> str:
        """从消息中提取订单号"""
        import re
        # 匹配常见订单号格式
        patterns = [
            r'OG\d{10,}',
            r'\d{10,}',
            r'[A-Z]{2}\d{8,}',
        ]
        for pattern in patterns:
            match = re.search(pattern, message)
            if match:
                return match.group()
        return "unknown"

    async def run(
        self,
        message: str,
        session_id: str,
        user_info: Optional[Dict] = None,
        chat_history: Optional[List] = None,
    ) -> Dict[str, Any]:
        """运行客服对话工作流"""
        initial_state = {
            "message": message,
            "session_id": session_id,
            "user_info": user_info,
            "chat_history": chat_history or [],
            "intent": "",
            "intent_confidence": 0.0,
            "retrieved_context": "",
            "tracking_info": None,
            "response": "",
            "needs_human_transfer": False,
            "actions_taken": [],
        }

        result = await self.graph.ainvoke(initial_state)

        return {
            "response": result.get("response", ""),
            "intent": result.get("intent", ""),
            "needs_human_transfer": result.get("needs_human_transfer", False),
            "actions_taken": result.get("actions_taken", []),
            "session_id": session_id,
        }
