package com.tourismgov.report.service; // Place it inside your security package

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class FeignClientInterceptor implements RequestInterceptor {
    
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles"; // ✅ MATCHED: Plural header matching GatewayHeaderFilter

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            // 1. Forward the raw Authorization JWT token if available
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null) {
                template.header("Authorization", authHeader); 
            }
            
            // 2. Forward the User ID string token 
            String xUserId = request.getHeader(HEADER_USER_ID);
            if (xUserId != null) {
                template.header(HEADER_USER_ID, xUserId);
            }
            
            // 3. ✅ FIXED: Forward using the plural "X-User-Roles" key required by your security filter context
            String xUserRoles = request.getHeader(HEADER_USER_ROLES);
            if (xUserRoles == null) {
                // Fallback: Check if singular was sent by an external provider, then map it to plural
                xUserRoles = request.getHeader("X-User-Role"); 
            }
            
            if (xUserRoles != null) {
                template.header(HEADER_USER_ROLES, xUserRoles);
            }
        }
    }
}