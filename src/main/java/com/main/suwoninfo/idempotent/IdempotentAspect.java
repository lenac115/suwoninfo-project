package com.main.suwoninfo.idempotent;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.suwoninfo.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    @Around("@annotation(com.main.suwoninfo.idempotent.Idempotent) || @within(com.main.suwoninfo.idempotent.Idempotent)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method m = sig.getMethod();
        Class<?> targetClass = AopUtils.getTargetClass(pjp);
        EvaluationContext ctx = new MethodBasedEvaluationContext(null, m, pjp.getArgs(), new DefaultParameterNameDiscoverer());


        Idempotent methodAnn = AnnotationUtils.findAnnotation(m, Idempotent.class);
        Idempotent classAnn = AnnotationUtils.findAnnotation(targetClass, Idempotent.class);
        Idempotent ann = (methodAnn != null) ? methodAnn : classAnn;
        if (ann == null) return pjp.proceed();

        String scope = ann.scope();
        String userId = eval(ann.user(), ctx);
        String idemKey = eval(ann.key(), ctx);
        String contentStr = eval(ann.content(), ctx);

        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("유저 미존재");
        if (idemKey == null || idemKey.isBlank()) throw new IllegalArgumentException("멱등키 미존재");

        Duration ttl = Duration.ofSeconds(ann.ttlSeconds());
        Duration dttl = Duration.ofSeconds(ann.dedupeTtlSeconds());

        String dupKey = null;
        if (!contentStr.isBlank()) {
            String canon = canonicalize(contentStr);
            String sigHash = sha256(canon);
            dupKey = String.format("dup:%s:%s:%s", scope, userId, sigHash);

            String prev = redisUtils.get(dupKey).toString();
            if (prev != null && ann.replayResponse()) {
                return readResponse(prev);
            } else if (prev != null) {
                return ResponseEntity.status(409).body("중복 컨텐츠");
            }
        }

        if (!idemKey.isBlank()) {
            String statKey = String.format("stat:%s:%s:%s", scope, userId, idemKey);
            Boolean first = redisUtils.setIfAbsent(statKey, "PENDING", ttl);

            if (Boolean.FALSE.equals(first)) {
                if (ann.replayResponse()) {
                    String respKey = statKey + ":resp";
                    String payload = redisUtils.get(respKey).toString();
                    if (payload != null) return readResponse(payload);
                }
                if ("DONE".equals(redisUtils.get(statKey))) return ResponseEntity.status(201).body("이미 완료됨");
                return ResponseEntity.status(409).body("실행 진행중");
            }

            try {
                Object result = pjp.proceed();
                if (ann.replayResponse() && result instanceof ResponseEntity<?> re) {
                    String payload = writeResponse(re);
                    redisUtils.set(statKey + ":resp", payload, ttl);
                }
                redisUtils.set(statKey, "DONE", ttl);

                if (dupKey != null && ann.replayResponse()) {
                    String payload = redisUtils.get(statKey + ":resp").toString();
                    if (payload != null) return readResponse(payload);
                } else if (dupKey != null) {
                    redisUtils.set(dupKey, "1", dttl);
                }
                return result;
            } catch (Throwable e) {
                redisUtils.delete(statKey);
                throw e;
            }
        }

        if (dupKey != null) {
            String prev = redisUtils.get(dupKey).toString();
            if (prev != null && ann.replayResponse()) return readResponse(prev);
        }
        Object result = pjp.proceed();
        if (dupKey != null && ann.replayResponse() && result instanceof ResponseEntity<?> re) {
            redisUtils.set(dupKey, writeResponse(re), dttl);
        }
        return result;
    }

    private String eval(String spel, EvaluationContext ctx) throws Throwable {
        if (spel == null || spel.isBlank()) return "";
        return String.valueOf(new SpelExpressionParser().parseExpression(spel).getValue(ctx));
    }

    private String canonicalize(String content) {
        return content.replaceAll("\\s+", " ").trim();
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String writeResponse(ResponseEntity<?> re) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of("status", re.getStatusCode().value(), "body", re.getBody()));
    }

    private ResponseEntity<?> readResponse(String payload) throws JsonProcessingException {
        JsonNode n = objectMapper.readTree(payload);
        int status = n.get("status").asInt();
        JsonNode body = n.get("body");
        return ResponseEntity.status(status)
                .header("Idempotent-Replay", "true")
                .body(objectMapper.convertValue(body, Object.class));
    }
}
