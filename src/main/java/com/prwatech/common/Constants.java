package com.prwatech.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constants {

  public static final String PUBLIC_APIS_URL = "api/pub/**";
  public static final String IAM_APIS_URL = "api/iam/**";
  public static final String LOGOUT_API_URL = "api/iam/logout";
  public static final String ERROR = "Error";
  public static final String AUTH = "Authorization";
  public static final String TOKEN_TYPE = "Bearer Token";
  public static final String AUTH_PARAM_TYPE = "Header";
  public static final String AUTH_DATA_TYPE = "string";
  public static final String UNAUTHORIZED_USER_ERROR_MESSAGE =
      "User is not authorized to access this resource";
  public static final String USER_ACCOUNT_LOCKED_MESSAGE = "User account is locked";
  public static final String UNAUTHENTICATED_USER_ERROR_MESSAGE =
      "Unauthenticated User: Invalid token";
  public static final String AUTH_HEADER = "Authorization";
  public static final String TOKEN_SPLIT_REGEX = " ";
  public static final Integer DEFAULT_TOKEN_LENGTH = 2;
  public static final String INVALID_TOKEN_ERROR_MESSAGE = "Invalid token";
  public static final String USERNAME_PASSWORD_CONNECTION = "Username-Password-Authentication";
  public static final String DEFAULT_EMAIL = "mnbvcxzlkjh7845asdf@prwatech.com";
  public static final Integer MAX_LIMIT = 9999;
  public static final Integer MIN_LIMIT = 1000;
  public static final String FTSMS_SENDER_ID = "FSTSMS";
  public static final String FTSMS_ROUTE = "q";
  public static final String FTSMS_OTP_ROUT = "otp";
  public static final String DEFAULT_OTP_SMS_BODY = "Welcome to Prwatech, your OTP is : ";
  public static final int DEFAULT_FLASH = 0;
  public static final String DEFAULT_LANGUAGE = "english";
  public static Integer DEFAULT_PRICING = 0;
  public static String SUCCESSFUL = "Successful";
  public static String UNSUCCESSFUL = "Unsuccessful";
  public static String FORGET_PASSWORD_MAIL_SUBJECT = "Prwatech - Otp for forget password";
  public static String FORGET_PASSWORD_MAIL_BODY =
      "Hey there,\nWelcome to Prwatech! We got your request for reset password.\nYour otp for reset password is : ";
  public static Integer PAGINATION_UTIL_LIST_DEFAULT_INDEX = 0;
  public static String COURSE_QUERY_EMAIl_SUBJECT = "User Query from course!";
  public static String DEFAULT_SMS_PROVIDER_NUMBER="+12703722154";
  public static String DEFAULT_HELP_AND_SUPPORT_SUBJECT="User help and support!";
  public static String DEFAULT_QUIZ_URL="https://unsplash.com/photos/qDgTQOYk6B8";
  public static String REFERAL_BIT_1="KA";
  public static String REFERAL_BIT_2="PRTC";
  public static String NEW_USER_COUPON_ID="64f8c81101adc23e4d5953e1";

  public static Integer DEFAULT_REF_AMOUNT=500;

  public static String USER_ACCOUNT_DELETION_REQUEST = "User Account Deletion Request";
  public static String USER_ACCOUNT_DELETION_MESSAGE =
          "Dear XXXX,\n" +
                  "\n" +
                  "Your account has been successfully deleted. If you have any questions, " +
                  "feel free to contact us at support@prwatech.com.\n" +
                  "\n" +
                  "Thank you,\n" +
                  "Prwatech";
}
