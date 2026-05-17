"""Temporal Fusion Transformer 模型实现

基于 PyTorch 实现的 TFT 模型，用于多步时序预测。
输入：历史配送数据、天气、节假日、门店特征
输出：预测配送时效（小时）
"""
import torch
import torch.nn as nn
import torch.nn.functional as F
from typing import Dict, List, Optional, Tuple
import numpy as np


class GatedLinearUnit(nn.Module):
    """门控线性单元"""

    def __init__(self, input_dim: int, output_dim: int):
        super().__init__()
        self.fc = nn.Linear(input_dim, output_dim)
        self.gate = nn.Linear(input_dim, output_dim)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.fc(x) * torch.sigmoid(self.gate(x))


class GatedResidualNetwork(nn.Module):
    """门控残差网络 (GRN)"""

    def __init__(
        self,
        input_dim: int,
        hidden_dim: int,
        output_dim: int,
        context_dim: Optional[int] = None,
        dropout: float = 0.1,
    ):
        super().__init__()
        self.input_dim = input_dim
        self.output_dim = output_dim

        self.fc1 = nn.Linear(input_dim, hidden_dim)
        self.elu = nn.ELU()
        self.fc2 = nn.Linear(hidden_dim, output_dim)
        self.dropout = nn.Dropout(dropout)
        self.gate = GatedLinearUnit(output_dim, output_dim)
        self.layer_norm = nn.LayerNorm(output_dim)

        if context_dim is not None:
            self.context_fc = nn.Linear(context_dim, hidden_dim, bias=False)
        else:
            self.context_fc = None

        if input_dim != output_dim:
            self.skip = nn.Linear(input_dim, output_dim)
        else:
            self.skip = None

    def forward(
        self, x: torch.Tensor, context: Optional[torch.Tensor] = None
    ) -> torch.Tensor:
        residual = self.skip(x) if self.skip is not None else x

        hidden = self.fc1(x)
        if self.context_fc is not None and context is not None:
            hidden = hidden + self.context_fc(context)
        hidden = self.elu(hidden)
        hidden = self.fc2(hidden)
        hidden = self.dropout(hidden)
        hidden = self.gate(hidden)

        return self.layer_norm(hidden + residual)


class VariableSelectionNetwork(nn.Module):
    """变量选择网络 - 自动选择重要特征"""

    def __init__(
        self,
        input_dim: int,
        num_inputs: int,
        hidden_dim: int,
        context_dim: Optional[int] = None,
        dropout: float = 0.1,
    ):
        super().__init__()
        self.num_inputs = num_inputs
        self.hidden_dim = hidden_dim

        # 每个变量的 GRN
        self.grns = nn.ModuleList([
            GatedResidualNetwork(input_dim, hidden_dim, hidden_dim, context_dim, dropout)
            for _ in range(num_inputs)
        ])

        # 变量选择权重
        self.softmax_grn = GatedResidualNetwork(
            input_dim * num_inputs, hidden_dim, num_inputs, context_dim, dropout
        )

    def forward(
        self, inputs: List[torch.Tensor], context: Optional[torch.Tensor] = None
    ) -> Tuple[torch.Tensor, torch.Tensor]:
        # 拼接所有输入计算选择权重
        flattened = torch.cat(inputs, dim=-1)
        weights = F.softmax(self.softmax_grn(flattened, context), dim=-1)

        # 对每个变量应用 GRN
        processed = torch.stack(
            [grn(inp, context) for grn, inp in zip(self.grns, inputs)], dim=-1
        )

        # 加权求和
        weights_expanded = weights.unsqueeze(-2)
        output = (processed * weights_expanded).sum(dim=-1)

        return output, weights


class InterpretableMultiHeadAttention(nn.Module):
    """可解释多头注意力"""

    def __init__(self, d_model: int, n_heads: int, dropout: float = 0.1):
        super().__init__()
        self.n_heads = n_heads
        self.d_k = d_model // n_heads

        self.W_q = nn.Linear(d_model, d_model)
        self.W_k = nn.Linear(d_model, d_model)
        self.W_v = nn.Linear(d_model, d_model)
        self.W_o = nn.Linear(d_model, d_model)
        self.dropout = nn.Dropout(dropout)

    def forward(
        self,
        query: torch.Tensor,
        key: torch.Tensor,
        value: torch.Tensor,
        mask: Optional[torch.Tensor] = None,
    ) -> Tuple[torch.Tensor, torch.Tensor]:
        batch_size = query.size(0)

        Q = self.W_q(query).view(batch_size, -1, self.n_heads, self.d_k).transpose(1, 2)
        K = self.W_k(key).view(batch_size, -1, self.n_heads, self.d_k).transpose(1, 2)
        V = self.W_v(value).view(batch_size, -1, self.n_heads, self.d_k).transpose(1, 2)

        scores = torch.matmul(Q, K.transpose(-2, -1)) / np.sqrt(self.d_k)
        if mask is not None:
            scores = scores.masked_fill(mask == 0, -1e9)

        attn_weights = F.softmax(scores, dim=-1)
        attn_weights = self.dropout(attn_weights)

        context = torch.matmul(attn_weights, V)
        context = context.transpose(1, 2).contiguous().view(batch_size, -1, self.n_heads * self.d_k)
        output = self.W_o(context)

        # 返回平均注意力权重用于可解释性
        avg_attn = attn_weights.mean(dim=1)
        return output, avg_attn


class TemporalFusionTransformer(nn.Module):
    """Temporal Fusion Transformer 完整实现

    用于物流配送时效预测的多步时序模型。

    Args:
        num_static_features: 静态特征数量（门店属性等）
        num_time_varying_known: 已知时变特征数量（节假日、天气预报等）
        num_time_varying_observed: 观测时变特征数量（历史配送数据等）
        hidden_dim: 隐藏层维度
        num_heads: 注意力头数
        num_lstm_layers: LSTM 层数
        dropout: Dropout 比率
        forecast_horizon: 预测步长
        quantiles: 分位数列表（用于不确定性估计）
    """

    def __init__(
        self,
        num_static_features: int = 8,
        num_time_varying_known: int = 6,
        num_time_varying_observed: int = 10,
        hidden_dim: int = 64,
        num_heads: int = 4,
        num_lstm_layers: int = 2,
        dropout: float = 0.1,
        forecast_horizon: int = 24,
        quantiles: Optional[List[float]] = None,
    ):
        super().__init__()
        self.hidden_dim = hidden_dim
        self.forecast_horizon = forecast_horizon
        self.quantiles = quantiles or [0.1, 0.5, 0.9]
        self.num_quantiles = len(self.quantiles)

        # 静态特征嵌入
        self.static_embedding = nn.Linear(num_static_features, hidden_dim)
        self.static_grn = GatedResidualNetwork(hidden_dim, hidden_dim, hidden_dim, dropout=dropout)

        # 静态上下文向量
        self.static_context_variable_selection = GatedResidualNetwork(
            hidden_dim, hidden_dim, hidden_dim, dropout=dropout
        )
        self.static_context_enrichment = GatedResidualNetwork(
            hidden_dim, hidden_dim, hidden_dim, dropout=dropout
        )

        # 时变特征嵌入
        self.time_varying_known_embedding = nn.Linear(num_time_varying_known, hidden_dim)
        self.time_varying_observed_embedding = nn.Linear(num_time_varying_observed, hidden_dim)

        # 编码器 LSTM
        self.encoder_lstm = nn.LSTM(
            input_size=hidden_dim,
            hidden_size=hidden_dim,
            num_layers=num_lstm_layers,
            dropout=dropout if num_lstm_layers > 1 else 0,
            batch_first=True,
        )

        # 解码器 LSTM
        self.decoder_lstm = nn.LSTM(
            input_size=hidden_dim,
            hidden_size=hidden_dim,
            num_layers=num_lstm_layers,
            dropout=dropout if num_lstm_layers > 1 else 0,
            batch_first=True,
        )

        # 门控跳跃连接
        self.post_lstm_gate = GatedLinearUnit(hidden_dim, hidden_dim)
        self.post_lstm_norm = nn.LayerNorm(hidden_dim)

        # 自注意力层
        self.self_attention = InterpretableMultiHeadAttention(hidden_dim, num_heads, dropout)
        self.post_attn_gate = GatedLinearUnit(hidden_dim, hidden_dim)
        self.post_attn_norm = nn.LayerNorm(hidden_dim)

        # 位置前馈
        self.pos_ffn = GatedResidualNetwork(hidden_dim, hidden_dim, hidden_dim, dropout=dropout)
        self.pos_ffn_gate = GatedLinearUnit(hidden_dim, hidden_dim)
        self.pos_ffn_norm = nn.LayerNorm(hidden_dim)

        # 输出层 - 分位数回归
        self.output_layer = nn.Linear(hidden_dim, self.num_quantiles)

    def forward(
        self,
        static_features: torch.Tensor,
        time_varying_known_past: torch.Tensor,
        time_varying_observed_past: torch.Tensor,
        time_varying_known_future: torch.Tensor,
    ) -> Dict[str, torch.Tensor]:
        """
        前向传播

        Args:
            static_features: [batch, num_static_features] 静态特征
            time_varying_known_past: [batch, past_steps, num_known] 已知历史时变特征
            time_varying_observed_past: [batch, past_steps, num_observed] 观测历史时变特征
            time_varying_known_future: [batch, future_steps, num_known] 已知未来时变特征

        Returns:
            Dict 包含预测值和注意力权重
        """
        batch_size = static_features.size(0)
        past_steps = time_varying_known_past.size(1)

        # 1. 静态特征处理
        static_emb = self.static_embedding(static_features)
        static_context = self.static_grn(static_emb)
        cs_selection = self.static_context_variable_selection(static_context)
        cs_enrichment = self.static_context_enrichment(static_context)

        # 2. 时变特征嵌入
        known_past_emb = self.time_varying_known_embedding(time_varying_known_past)
        observed_past_emb = self.time_varying_observed_embedding(time_varying_observed_past)
        known_future_emb = self.time_varying_known_embedding(time_varying_known_future)

        # 3. 编码器输入：已知 + 观测特征融合
        encoder_input = known_past_emb + observed_past_emb
        # 加入静态上下文
        encoder_input = encoder_input + cs_selection.unsqueeze(1).expand_as(encoder_input)

        # 4. LSTM 编码
        encoder_output, (h_n, c_n) = self.encoder_lstm(encoder_input)

        # 5. 解码器输入
        decoder_input = known_future_emb + cs_selection.unsqueeze(1).expand(
            batch_size, self.forecast_horizon, self.hidden_dim
        )
        decoder_output, _ = self.decoder_lstm(decoder_input, (h_n, c_n))

        # 6. 拼接编码器和解码器输出
        lstm_output = torch.cat([encoder_output, decoder_output], dim=1)

        # 7. 门控跳跃连接
        gated_output = self.post_lstm_gate(lstm_output)
        gated_output = self.post_lstm_norm(gated_output + lstm_output)

        # 8. 静态上下文增强
        enriched = gated_output + cs_enrichment.unsqueeze(1).expand_as(gated_output)

        # 9. 自注意力（仅对解码器部分使用因果掩码）
        total_steps = past_steps + self.forecast_horizon
        mask = torch.ones(total_steps, total_steps, device=static_features.device)
        # 因果掩码：未来位置不能看到更远的未来
        for i in range(past_steps, total_steps):
            mask[i, i + 1:] = 0

        attn_output, attn_weights = self.self_attention(enriched, enriched, enriched, mask)
        attn_output = self.post_attn_gate(attn_output)
        attn_output = self.post_attn_norm(attn_output + enriched)

        # 10. 位置前馈
        ffn_output = self.pos_ffn(attn_output)
        ffn_output = self.pos_ffn_gate(ffn_output)
        ffn_output = self.pos_ffn_norm(ffn_output + attn_output)

        # 11. 取解码器部分输出
        decoder_ffn = ffn_output[:, past_steps:, :]

        # 12. 分位数输出
        quantile_output = self.output_layer(decoder_ffn)

        return {
            "quantile_predictions": quantile_output,  # [batch, horizon, num_quantiles]
            "attention_weights": attn_weights,
            "static_weights": cs_selection,
        }

    def predict(
        self,
        static_features: torch.Tensor,
        time_varying_known_past: torch.Tensor,
        time_varying_observed_past: torch.Tensor,
        time_varying_known_future: torch.Tensor,
    ) -> Dict[str, np.ndarray]:
        """推理接口，返回 numpy 数组"""
        self.eval()
        with torch.no_grad():
            output = self.forward(
                static_features,
                time_varying_known_past,
                time_varying_observed_past,
                time_varying_known_future,
            )
        predictions = output["quantile_predictions"].cpu().numpy()
        return {
            "lower": predictions[:, :, 0],  # 10% 分位数
            "median": predictions[:, :, 1],  # 50% 分位数（点预测）
            "upper": predictions[:, :, 2],  # 90% 分位数
        }


class QuantileLoss(nn.Module):
    """分位数损失函数"""

    def __init__(self, quantiles: List[float]):
        super().__init__()
        self.quantiles = quantiles

    def forward(self, predictions: torch.Tensor, targets: torch.Tensor) -> torch.Tensor:
        """
        Args:
            predictions: [batch, horizon, num_quantiles]
            targets: [batch, horizon]
        """
        losses = []
        for i, q in enumerate(self.quantiles):
            errors = targets - predictions[:, :, i]
            losses.append(torch.max((q - 1) * errors, q * errors))
        return torch.stack(losses, dim=-1).mean()
