package com.main.suwoninfo.exception;

import com.main.suwoninfo.form.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String AI_SERVER_URL = "http://localhost:8000/analysis";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("handleMethodArgumentNotValid", ex);
        return handleExceptionInternal(ex, CommonErrorCode.INVALID_PARAMETER);
    }


    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Object> handleCustomException(CustomException e) {
        return handleExceptionInternal(e.getErrorCode());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("handleIllegalArgument", e);
        return handleExceptionInternal(CommonErrorCode.INVALID_PARAMETER, e.getMessage());
    }


    @ExceptionHandler({Exception.class})
    public ResponseEntity<Object> handleAllException(Exception e) {
        log.warn("handleAllException", e);

        try {
            Map<String, String> requestPayload = new HashMap<>();
            requestPayload.put("project_name", "suwon_info");
            requestPayload.put("log_content", e.toString() + " : " + e.getMessage());

            StackTraceElement[] stackTrace = e.getStackTrace();
            if (stackTrace.length > 0) {
                StackTraceElement rootCause = stackTrace[0];
                String fileName = rootCause.getFileName();
                int lineNumber = rootCause.getLineNumber();

                String codeSnippet = readFileContext(fileName, lineNumber);
                requestPayload.put("code_context", codeSnippet);
            }

            Map<String, Object> aiAnalysisResult = restTemplate.postForObject(
                    AI_SERVER_URL,
                    requestPayload,
                    Map.class
            );

            if (aiAnalysisResult != null) {
                String originalSolution = (String) aiAnalysisResult.get("solution");
                if (originalSolution != null) {
                    String cleanSolution = originalSolution.replaceAll("```[\\s\\S]*?```", "").trim();
                    aiAnalysisResult.put("solution", cleanSolution);
                }
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(aiAnalysisResult);
            }
        } catch (Exception error) {
            log.error("AI 분석 서버 연결 실패: {}", error.getMessage());
        }
        return handleExceptionInternal(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private String readFileContext(String fileName, Integer lineNumber) {
        try {
            Path startPath = Paths.get("src/main/java");

            try (Stream<Path> stream = Files.walk(startPath)) {
                Optional<Path> filePath = stream
                        .filter(p -> p.getFileName().toString().equals(fileName))
                        .findFirst();

                if (filePath.isPresent()) {
                    List<String> lines = Files.readAllLines(filePath.get(), StandardCharsets.UTF_8);

                    int targetIndex = lineNumber - 1;
                    if (targetIndex >= 0 && targetIndex < lines.size()) {
                        int start = Math.max(0, targetIndex - 2);
                        int end = Math.min(lines.size(), targetIndex + 3);

                        StringBuilder context = new StringBuilder();
                        for (int i = start; i < end; i++) {
                            context.append(String.format("%d: %s\n", i + 1, lines.get(i)));
                            if (i == targetIndex) context.append("   ^^^^^^ [Error Here]\n");
                        }
                        return context.toString();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("소스 코드 읽기 실패: " + e.getMessage());
        }
        return "Source code not available (Local only).";
    }

    private ResponseEntity<Object> handleExceptionInternal(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(makeErrorResponse(errorCode));
    }

    private ErrorResponse makeErrorResponse(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .code(errorCode.name())
                .message(errorCode.getMessage())
                .build();
    }

    private ResponseEntity<Object> handleExceptionInternal(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(makeErrorResponse(errorCode, message));
    }

    private ErrorResponse makeErrorResponse(ErrorCode errorCode, String message) {
        return ErrorResponse.builder()
                .code(errorCode.name())
                .message(message)
                .build();
    }

    private ResponseEntity<Object> handleExceptionInternal(BindException e, ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(makeErrorResponse(e, errorCode));
    }

    private ErrorResponse makeErrorResponse(BindException e, ErrorCode errorCode) {
        List<ErrorResponse.ValidationError> validationErrorList = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(ErrorResponse.ValidationError::of)
                .collect(Collectors.toList());

        return ErrorResponse.builder()
                .code(errorCode.name())
                .message(errorCode.getMessage())
                .errors(validationErrorList)
                .build();
    }

}
