package com.orange.logistics.im.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息编解码器
 * 负责 ImMessage JSON 字符串之间的转换
 */
@Slf4j
@Component
public class MessageCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 编码：ImMessage -> JSON 字符     */
    public String encode(ImMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("[Codec] 消息编码失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解码：JSON 字符-> ImMessage
     */
    public ImMessage decode(String json) {
        try {
            return objectMapper.readValue(json, ImMessage.class);
        } catch (JsonProcessingException e) {
            log.error("[Codec] 消息解码失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 编码并发送消息到指定 Channel
     */
    public void sendMessage(ChannelHandlerContext ctx, ImMessage message) {
        String json = encode(message);
        if (json != null) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame(json));
        }
    }

    /**
     * 编码TextWebSocketFrame
     */
    public TextWebSocketFrame encodeToFrame(ImMessage message) {
        String json = encode(message);
        return json != null ? new TextWebSocketFrame(json) : null;
    }
}
