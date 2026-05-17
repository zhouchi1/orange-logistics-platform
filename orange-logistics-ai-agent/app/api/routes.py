"""API 路由"""
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, Field
from typing import Dict, List, Optional, Any
import logging

logger = logging.getLogger(__name__)

router = APIRouter()


class AgentRequest(BaseModel):
    """Agent 请求"""
    session_id: str = Field(description="会话ID")
    message: str = Field(description="用户消息")
    agent_type: str = Field(default="dispatch", description="Agent类型: dispatch/customer/analytics/anomaly")
    context: Optional[Dict[str, Any]] = Field(default=None, description="附加上下文")


class AgentResponse(BaseModel):
    """Agent 响应"""
    session_id: str
    agent_type: str
    response: str
    status: str = "success"
    metadata: Optional[Dict[str, Any]] = None


@router.post("/chat", response_model=AgentResponse)
async def chat(request: AgentRequest, req: Request):
    """与 Agent 对话"""
    try:
        from app.agents.dispatch_agent import DispatchAgent

        # 获取对话历史
        memory_manager = req.app.state.memory_manager
        history = await memory_manager.get_history(request.session_id)

        if request.agent_type == "dispatch":
            agent = DispatchAgent()
            result = await agent.dispatch(
                {"type": "general", "message": request.message},
                chat_history=history,
            )
        else:
            result = {"status": "success", "decision": f"Agent [{request.agent_type}] 暂未实现"}

        # 保存对话
        await memory_manager.add_message(request.session_id, "user", request.message)
        await memory_manager.add_message(request.session_id, "assistant", result.get("decision", ""))

        return AgentResponse(
            session_id=request.session_id,
            agent_type=request.agent_type,
            response=result.get("decision", "处理完成"),
            status=result.get("status", "success"),
            metadata=result,
        )
    except Exception as e:
        logger.error(f"Agent chat error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/agents")
async def list_agents():
    """列出可用 Agent"""
    return {
        "agents": [
            {"name": "dispatch", "description": "智能调度Agent - 订单分配、路径优化、运力调度"},
            {"name": "customer", "description": "客户服务Agent - 查件、投诉、咨询"},
            {"name": "analytics", "description": "数据分析Agent - KPI分析、趋势预测"},
            {"name": "anomaly", "description": "异常检测Agent - 延误预警、风险识别"},
        ]
    }


@router.post("/knowledge/search")
async def knowledge_search(query: str, top_k: int = 5, req: Request = None):
    """知识库搜索"""
    vector_store = req.app.state.vector_store
    results = await vector_store.search(query, top_k=top_k)
    return {"query": query, "results": results, "total": len(results)}
