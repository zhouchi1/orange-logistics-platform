"""运营分析 Agent

基于 LangChain 实现的运营数据分析 Agent。
能力：自然语言查询数据、生成分析报告、趋势预测。
"""
from typing import Dict, List, Optional, Any
from langchain_openai import ChatOpenAI
from langchain.agents import AgentExecutor
from langchain.agents.format_scratchpad.openai_tools import format_to_openai_tool_messages
from langchain.agents.output_parsers.openai_tools import OpenAIToolsAgentOutputParser
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
import logging

from app.config import settings
from app.prompts.analytics_prompt import ANALYTICS_SYSTEM_PROMPT
from app.tools.database_tool import DatabaseTool
from app.tools.prediction_tool import PredictionTool

logger = logging.getLogger(__name__)


class AnalyticsAgent:
    """运营分析 Agent

    职责：
    1. 自然语言转 SQL 查询
    2. 数据分析和可视化建议
    3. 运营指标监控
    4. 趋势预测和异常检测
    """

    def __init__(self):
        self.llm = ChatOpenAI(
            model=settings.LLM_MODEL,
            temperature=0.0,
            max_tokens=settings.LLM_MAX_TOKENS,
            openai_api_key=settings.OPENAI_API_KEY,
            openai_api_base=settings.OPENAI_API_BASE,
        )

        self.tools = [
            DatabaseTool(),
            PredictionTool(),
        ]

        self.agent_executor = self._build_agent()

    def _build_agent(self) -> AgentExecutor:
        """构建分析 Agent"""
        prompt = ChatPromptTemplate.from_messages([
            ("system", ANALYTICS_SYSTEM_PROMPT),
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

    async def query(
        self,
        question: str,
        context: Optional[Dict] = None,
        chat_history: Optional[List] = None,
    ) -> Dict[str, Any]:
        """处理分析查询

        Args:
            question: 自然语言问题
            context: 上下文信息
            chat_history: 对话历史

        Returns:
            分析结果
        """
        # 增强问题上下文
        enhanced_input = question
        if context:
            time_range = context.get("time_range", "")
            region = context.get("region", "")
            if time_range:
                enhanced_input += f"\n时间范围：{time_range}"
            if region:
                enhanced_input += f"\n区域：{region}"

        try:
            result = await self.agent_executor.ainvoke({
                "input": enhanced_input,
                "chat_history": chat_history or [],
            })

            return {
                "status": "success",
                "answer": result["output"],
                "query": question,
                "data_sources": self._extract_data_sources(result),
            }
        except Exception as e:
            logger.error(f"Analytics agent error: {e}")
            return {
                "status": "error",
                "error": str(e),
                "answer": "抱歉，无法完成数据分析，请检查查询条件后重试。",
            }

    def _extract_data_sources(self, result: Dict) -> List[str]:
        """提取使用的数据源"""
        sources = set()
        intermediate_steps = result.get("intermediate_steps", [])
        for step in intermediate_steps:
            if hasattr(step[0], "tool"):
                sources.add(step[0].tool)
        return list(sources)
