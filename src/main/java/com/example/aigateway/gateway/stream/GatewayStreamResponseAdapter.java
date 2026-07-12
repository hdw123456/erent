package com.example.aigateway.gateway.stream;

import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.provider.ProviderStreamEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts provider-neutral stream events into public gateway wire protocols. */
@Component
public class GatewayStreamResponseAdapter {
    private final ObjectMapper objectMapper;

    public GatewayStreamResponseAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Session open(GatewayStreamProtocol protocol, String requestId, String model) {
        return new Session(protocol, requestId, model);
    }

    /** Reconstructs a terminal stream when an idempotent request is replayed. */
    public List<GatewayStreamFrame> replay(
            GatewayStreamProtocol protocol,
            ChatResponse response) {
        Session session = open(protocol, response.getRequestId(), response.getModel());
        if (protocol == GatewayStreamProtocol.OPENAI_CHAT_COMPLETIONS) {
            return session.openAiReplay(response);
        }

        String content = response.getMessage() == null ? "" : response.getMessage().getContent();
        String role = response.getMessage() == null ? "assistant" : response.getMessage().getRole();
        ProviderStreamEvent replayEvent = new ProviderStreamEvent(
                null,
                response.getId(),
                response.getModel(),
                role,
                content,
                response.getFinishReason(),
                response.getUsage(),
                false);
        List<GatewayStreamFrame> frames = new ArrayList<>(session.accept(replayEvent));
        frames.addAll(session.finish(response));
        return frames;
    }

    /** Creates a protocol-shaped terminal error event. */
    public List<GatewayStreamFrame> error(
            GatewayStreamProtocol protocol,
            BusinessException exception,
            String requestId) {
        ObjectNode error = objectMapper.createObjectNode();
        return switch (protocol) {
            case OPENAI_CHAT_COMPLETIONS -> {
                ObjectNode body = objectMapper.createObjectNode();
                error.put("code", exception.getCode());
                error.put("message", exception.getMessage());
                error.put("request_id", requestId);
                body.set("error", error);
                yield List.of(GatewayStreamFrame.data(write(body)), GatewayStreamFrame.data("[DONE]"));
            }
            case ANTHROPIC_MESSAGES -> {
                ObjectNode body = objectMapper.createObjectNode();
                body.put("type", "error");
                error.put("type", exception.getCode());
                error.put("message", exception.getMessage());
                body.set("error", error);
                yield List.of(new GatewayStreamFrame("error", write(body)));
            }
            case OPENAI_RESPONSES -> {
                ObjectNode body = objectMapper.createObjectNode();
                body.put("type", "error");
                body.put("sequence_number", 0);
                body.put("code", exception.getCode());
                body.put("message", exception.getMessage());
                body.putNull("param");
                body.put("request_id", requestId);
                yield List.of(new GatewayStreamFrame("error", write(body)));
            }
        };
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize stream event", exception);
        }
    }

    /** Holds ordering state for one downstream stream. */
    public final class Session {
        private final GatewayStreamProtocol protocol;
        private final String requestId;
        private final String model;
        private final String chatCompletionId;
        private final String anthropicMessageId;
        private final String responseId;
        private final String responseMessageId;
        private final long createdAt = Instant.now().getEpochSecond();

        private boolean started;
        private long sequenceNumber;

        private Session(GatewayStreamProtocol protocol, String requestId, String model) {
            this.protocol = protocol;
            this.requestId = requestId;
            this.model = model;
            this.chatCompletionId = prefixedId("chatcmpl_", requestId);
            this.anthropicMessageId = prefixedId("msg_", requestId);
            this.responseId = prefixedId("resp_", requestId);
            this.responseMessageId = prefixedId("msg_", requestId);
        }

        public List<GatewayStreamFrame> accept(ProviderStreamEvent event) {
            if (event.done()) {
                return List.of();
            }
            return switch (protocol) {
                case OPENAI_CHAT_COMPLETIONS -> openAiEvent(event);
                case ANTHROPIC_MESSAGES -> anthropicEvent(event);
                case OPENAI_RESPONSES -> responsesEvent(event);
            };
        }

        /** Emits terminal lifecycle events only after usage has been persisted. */
        public List<GatewayStreamFrame> finish(ChatResponse response) {
            return switch (protocol) {
                case OPENAI_CHAT_COMPLETIONS -> List.of(GatewayStreamFrame.data("[DONE]"));
                case ANTHROPIC_MESSAGES -> anthropicFinish(response);
                case OPENAI_RESPONSES -> responsesFinish(response);
            };
        }

        private List<GatewayStreamFrame> openAiEvent(ProviderStreamEvent event) {
            if (event.rawData() != null && !event.rawData().isBlank()) {
                return List.of(GatewayStreamFrame.data(event.rawData()));
            }
            if (event.textDelta() == null) {
                return List.of();
            }
            return List.of(GatewayStreamFrame.data(write(openAiChunk(
                    event.role(), event.textDelta(), event.finishReason(), null))));
        }

        private List<GatewayStreamFrame> openAiReplay(ChatResponse response) {
            String role = response.getMessage() == null ? "assistant" : response.getMessage().getRole();
            String content = response.getMessage() == null ? "" : response.getMessage().getContent();
            List<GatewayStreamFrame> frames = new ArrayList<>();
            frames.add(GatewayStreamFrame.data(write(openAiChunk(role, content, null, null))));
            frames.add(GatewayStreamFrame.data(write(openAiChunk(
                    null, null, response.getFinishReason(), null))));
            frames.add(GatewayStreamFrame.data(write(openAiChunk(null, null, null, response.getUsage()))));
            frames.add(GatewayStreamFrame.data("[DONE]"));
            return frames;
        }

        private ObjectNode openAiChunk(
                String role,
                String content,
                String finishReason,
                ChatResponse.Usage usage) {
            ObjectNode chunk = objectMapper.createObjectNode();
            chunk.put("id", chatCompletionId);
            chunk.put("object", "chat.completion.chunk");
            chunk.put("created", createdAt);
            chunk.put("model", model);
            ArrayNode choices = chunk.putArray("choices");

            if (usage == null || role != null || content != null || finishReason != null) {
                ObjectNode choice = choices.addObject();
                choice.put("index", 0);
                ObjectNode delta = choice.putObject("delta");
                if (role != null) {
                    delta.put("role", role);
                }
                if (content != null) {
                    delta.put("content", content);
                }
                if (finishReason == null) {
                    choice.putNull("finish_reason");
                } else {
                    choice.put("finish_reason", finishReason);
                }
            }
            if (usage != null) {
                chunk.set("usage", openAiUsage(usage));
            }
            return chunk;
        }

        private List<GatewayStreamFrame> anthropicEvent(ProviderStreamEvent event) {
            List<GatewayStreamFrame> frames = new ArrayList<>();
            ensureAnthropicStarted(frames, token(event.usage(), true));
            if (event.textDelta() != null && !event.textDelta().isEmpty()) {
                ObjectNode body = type("content_block_delta");
                body.put("index", 0);
                ObjectNode delta = body.putObject("delta");
                delta.put("type", "text_delta");
                delta.put("text", event.textDelta());
                frames.add(new GatewayStreamFrame("content_block_delta", write(body)));
            }
            return frames;
        }

        private void ensureAnthropicStarted(List<GatewayStreamFrame> frames, int inputTokens) {
            if (started) {
                return;
            }
            started = true;

            ObjectNode messageStart = type("message_start");
            ObjectNode message = messageStart.putObject("message");
            message.put("id", anthropicMessageId);
            message.put("type", "message");
            message.put("role", "assistant");
            message.put("model", model);
            message.putArray("content");
            message.putNull("stop_reason");
            message.putNull("stop_sequence");
            message.putObject("usage").put("input_tokens", inputTokens);
            frames.add(new GatewayStreamFrame("message_start", write(messageStart)));

            ObjectNode blockStart = type("content_block_start");
            blockStart.put("index", 0);
            ObjectNode block = blockStart.putObject("content_block");
            block.put("type", "text");
            block.put("text", "");
            frames.add(new GatewayStreamFrame("content_block_start", write(blockStart)));
        }

        private List<GatewayStreamFrame> anthropicFinish(ChatResponse response) {
            List<GatewayStreamFrame> frames = new ArrayList<>();
            ensureAnthropicStarted(frames, token(response.getUsage(), true));

            ObjectNode blockStop = type("content_block_stop");
            blockStop.put("index", 0);
            frames.add(new GatewayStreamFrame("content_block_stop", write(blockStop)));

            ObjectNode messageDelta = type("message_delta");
            ObjectNode delta = messageDelta.putObject("delta");
            delta.put("stop_reason", anthropicStopReason(response.getFinishReason()));
            delta.putNull("stop_sequence");
            messageDelta.putObject("usage").put("output_tokens", token(response.getUsage(), false));
            frames.add(new GatewayStreamFrame("message_delta", write(messageDelta)));

            ObjectNode messageStop = type("message_stop");
            frames.add(new GatewayStreamFrame("message_stop", write(messageStop)));
            return frames;
        }

        private List<GatewayStreamFrame> responsesEvent(ProviderStreamEvent event) {
            List<GatewayStreamFrame> frames = new ArrayList<>();
            ensureResponsesStarted(frames);
            if (event.textDelta() != null && !event.textDelta().isEmpty()) {
                ObjectNode delta = responseType("response.output_text.delta");
                delta.put("item_id", responseMessageId);
                delta.put("output_index", 0);
                delta.put("content_index", 0);
                delta.put("delta", event.textDelta());
                frames.add(namedResponseFrame(delta));
            }
            return frames;
        }

        private void ensureResponsesStarted(List<GatewayStreamFrame> frames) {
            if (started) {
                return;
            }
            started = true;

            ObjectNode created = responseType("response.created");
            created.set("response", responseObject("in_progress", "", null, false));
            frames.add(namedResponseFrame(created));

            ObjectNode inProgress = responseType("response.in_progress");
            inProgress.set("response", responseObject("in_progress", "", null, false));
            frames.add(namedResponseFrame(inProgress));

            ObjectNode itemAdded = responseType("response.output_item.added");
            itemAdded.put("output_index", 0);
            itemAdded.set("item", outputMessage("in_progress", ""));
            frames.add(namedResponseFrame(itemAdded));

            ObjectNode partAdded = responseType("response.content_part.added");
            partAdded.put("item_id", responseMessageId);
            partAdded.put("output_index", 0);
            partAdded.put("content_index", 0);
            partAdded.set("part", outputPart(""));
            frames.add(namedResponseFrame(partAdded));
        }

        private List<GatewayStreamFrame> responsesFinish(ChatResponse response) {
            List<GatewayStreamFrame> frames = new ArrayList<>();
            ensureResponsesStarted(frames);
            String content = response.getMessage() == null || response.getMessage().getContent() == null
                    ? ""
                    : response.getMessage().getContent();

            ObjectNode textDone = responseType("response.output_text.done");
            textDone.put("item_id", responseMessageId);
            textDone.put("output_index", 0);
            textDone.put("content_index", 0);
            textDone.put("text", content);
            frames.add(namedResponseFrame(textDone));

            ObjectNode partDone = responseType("response.content_part.done");
            partDone.put("item_id", responseMessageId);
            partDone.put("output_index", 0);
            partDone.put("content_index", 0);
            partDone.set("part", outputPart(content));
            frames.add(namedResponseFrame(partDone));

            ObjectNode itemDone = responseType("response.output_item.done");
            itemDone.put("output_index", 0);
            itemDone.set("item", outputMessage("completed", content));
            frames.add(namedResponseFrame(itemDone));

            ObjectNode completed = responseType("response.completed");
            completed.set("response", responseObject("completed", content, response.getUsage(), true));
            frames.add(namedResponseFrame(completed));
            return frames;
        }

        private ObjectNode responseObject(
                String status,
                String content,
                ChatResponse.Usage usage,
                boolean includeOutput) {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("id", responseId);
            response.put("object", "response");
            response.put("created_at", createdAt);
            response.put("status", status);
            response.putNull("error");
            response.putNull("incomplete_details");
            response.put("model", model);
            ArrayNode output = response.putArray("output");
            if (includeOutput) {
                output.add(outputMessage("completed", content));
            }
            if (usage == null) {
                response.putNull("usage");
            } else {
                response.set("usage", responsesUsage(usage));
            }
            return response;
        }

        private ObjectNode outputMessage(String status, String content) {
            ObjectNode message = objectMapper.createObjectNode();
            message.put("id", responseMessageId);
            message.put("type", "message");
            message.put("status", status);
            message.put("role", "assistant");
            ArrayNode contentArray = message.putArray("content");
            if (!content.isEmpty() || "completed".equals(status)) {
                contentArray.add(outputPart(content));
            }
            return message;
        }

        private ObjectNode outputPart(String content) {
            ObjectNode part = objectMapper.createObjectNode();
            part.put("type", "output_text");
            part.put("text", content);
            part.putArray("annotations");
            return part;
        }

        private ObjectNode responseType(String type) {
            ObjectNode node = type(type);
            node.put("sequence_number", sequenceNumber++);
            return node;
        }

        private GatewayStreamFrame namedResponseFrame(ObjectNode node) {
            return new GatewayStreamFrame(node.path("type").asText(), write(node));
        }

        private ObjectNode type(String type) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", type);
            return node;
        }

        private ObjectNode openAiUsage(ChatResponse.Usage usage) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("prompt_tokens", token(usage, true));
            node.put("completion_tokens", token(usage, false));
            node.put("total_tokens", usage == null || usage.getTotalTokens() == null
                    ? token(usage, true) + token(usage, false)
                    : usage.getTotalTokens());
            return node;
        }

        private ObjectNode responsesUsage(ChatResponse.Usage usage) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("input_tokens", token(usage, true));
            node.put("output_tokens", token(usage, false));
            node.put("total_tokens", usage == null || usage.getTotalTokens() == null
                    ? token(usage, true) + token(usage, false)
                    : usage.getTotalTokens());
            return node;
        }

        private int token(ChatResponse.Usage usage, boolean input) {
            if (usage == null) {
                return 0;
            }
            Integer value = input ? usage.getPromptTokens() : usage.getCompletionTokens();
            return value == null ? 0 : value;
        }

        private String anthropicStopReason(String finishReason) {
            if (finishReason == null || finishReason.isBlank() || "stop".equals(finishReason)) {
                return "end_turn";
            }
            return switch (finishReason) {
                case "length" -> "max_tokens";
                case "tool_calls" -> "tool_use";
                default -> finishReason;
            };
        }

        private String prefixedId(String prefix, String value) {
            if (value != null && value.startsWith(prefix)) {
                return value;
            }
            String suffix = value == null || value.isBlank() ? "unknown" : value.replace("req_", "");
            return prefix + suffix;
        }
    }
}
