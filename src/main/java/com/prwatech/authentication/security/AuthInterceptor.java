package com.prwatech.authentication.security;

import static com.prwatech.common.Constants.UNAUTHENTICATED_USER_ERROR_MESSAGE;
import static com.prwatech.common.Constants.UNAUTHORIZED_USER_ERROR_MESSAGE;
import static com.prwatech.common.Constants.USER_ACCOUNT_LOCKED_MESSAGE;
import static org.slf4j.LoggerFactory.getLogger;

import com.prwatech.common.exception.ForbiddenException;
import com.prwatech.common.exception.LockedException;
import com.prwatech.common.exception.UnAuthorizedException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Configuration
@Transactional
@AllArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

  private static final Logger LOGGER = getLogger(AuthInterceptor.class);

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    try {

      // Get the token and verify to autho;

      return true;

    } catch (ForbiddenException e) {
      LOGGER.error(UNAUTHORIZED_USER_ERROR_MESSAGE, e.getMessage());
      throw new ForbiddenException(UNAUTHORIZED_USER_ERROR_MESSAGE);
    } catch (LockedException e) {
      LOGGER.error(USER_ACCOUNT_LOCKED_MESSAGE, e.getMessage());
      throw new UnAuthorizedException(USER_ACCOUNT_LOCKED_MESSAGE);
    } catch (Exception e) {
      LOGGER.error(UNAUTHENTICATED_USER_ERROR_MESSAGE, e.getMessage());
      throw new UnAuthorizedException(UNAUTHENTICATED_USER_ERROR_MESSAGE);
    }
  }

  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      ModelAndView modelAndView)
      throws Exception {
    HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
  }
}
