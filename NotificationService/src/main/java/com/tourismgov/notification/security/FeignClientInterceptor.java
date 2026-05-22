package com.tourismgov.notification.security; 

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class FeignClientInterceptor implements RequestInterceptor {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String INTERNAL_CALL_HEADER = "X-Internal-Call";
    private static final String INTERNAL_SECRET = "SecretMicroserviceToken123";

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            String userId = request.getHeader(HEADER_USER_ID);
            String userRoles = request.getHeader(HEADER_USER_ROLES);
            String userEmail = request.getHeader(HEADER_USER_EMAIL);
            
            if (authHeader != null) {
                template.header(HttpHeaders.AUTHORIZATION, authHeader);
            }
            if (userId != null) {
                template.header(HEADER_USER_ID, userId);
            }
            if (userRoles != null) {
                template.header(HEADER_USER_ROLES, userRoles);
            }
            if (userEmail != null) {
                template.header(HEADER_USER_EMAIL, userEmail);
            }
        }
        
        // ✅ INJECT INTERNAL SECRET: Identifies this call as a trusted microservice request
        template.header(INTERNAL_CALL_HEADER, INTERNAL_SECRET);
    }
}