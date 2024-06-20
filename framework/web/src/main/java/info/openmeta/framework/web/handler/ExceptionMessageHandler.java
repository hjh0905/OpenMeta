package info.openmeta.framework.web.handler;

import info.openmeta.framework.web.aspect.ApiExceptionAspect;
import info.openmeta.framework.base.context.ContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Exception message handler
 */
@Component
public class ExceptionMessageHandler {

    @Autowired
    private HttpServletRequest request;

    /**
     * Get the current request URI
     * @return URI as a string
     */
    public String getRequestURI() {
        return request != null ? request.getRequestURI() : StringUtils.EMPTY;
    }

    /**
     * Get the current request info, including the URI, request parameters, request body, and traceId.
     *
     * @return A concatenated string of request info.
     */
    public String getRequestInfo() {
        StringBuilder builder = new StringBuilder(" Request: ");
        if (request != null) {
            builder.append(request.getRequestURL().toString());
            String queryString = request.getQueryString();
            if (StringUtils.isNotBlank(queryString)) {
                builder.append("?").append(queryString);
            }
            appendUserAndTraceId(builder);
            appendClientIp(builder);
            appendRequestParams(builder);
        } else if (ContextHolder.getContext() != null) {
            appendUserAndTraceId(builder);
        }
        return builder.toString();
    }

    /**
     * Append user info and TraceID to the builder.
     */
    private void appendUserAndTraceId(StringBuilder builder) {
        if (ContextHolder.getContext() != null) {
            builder.append(" ; User: ").append(ContextHolder.getContext().getName());
            builder.append(" ; TraceID: ").append(ContextHolder.getContext().getRequestId());
        } else {
            String traceId = request.getHeader("traceId");
            if (StringUtils.isNotBlank(traceId)) {
                builder.append(" ; TraceID: ").append(traceId);
            }
        }
    }

    /**
     * Append client IP to the builder, considering proxy headers.
     */
    private void appendClientIp(StringBuilder builder) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(clientIp)) {
            builder.append(" ; ClientIP: ").append(clientIp);
        } else {
            clientIp = request.getHeader("X-Real-IP");
            if (StringUtils.isNotBlank(clientIp)) {
                builder.append(" ; ClientRealIP: ").append(clientIp);
            }
        }
    }

    /**
     * Appends request parameters to the builder.
     */
    private void appendRequestParams(StringBuilder builder) {
        String receivedParams = (String) request.getAttribute(ApiExceptionAspect.REQUEST_PARAMS);
        if (StringUtils.isNotBlank(receivedParams)) {
            builder.append("\n").append(receivedParams);
        }
    }
}