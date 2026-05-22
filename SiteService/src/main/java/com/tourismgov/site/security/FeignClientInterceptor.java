package com.tourismgov.site.security;



import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class FeignClientInterceptor implements RequestInterceptor {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    public void apply(RequestTemplate template) {
        // Retrieve the current incoming request attributes from the thread local context
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            String userId = request.getHeader(HEADER_USER_ID);
            String userRoles = request.getHeader(HEADER_USER_ROLES);
            
            // Forward the security headers to the downstream microservice
            if (userId != null) {
                template.header(HEADER_USER_ID, userId);
            }
            if (userRoles != null) {
                template.header(HEADER_USER_ROLES, userRoles);
            }
        }
    }
}
