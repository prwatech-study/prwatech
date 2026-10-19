package com.prwatech.skillama.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * SMTP login for Skillama learner mail only. Prwatech mail keeps {@code javaMailSender}.
 */
@Configuration
public class SkillamaMailConfiguration {

    @Bean(name = "skillamaMailSender")
    public JavaMailSender skillamaMailSender(
            @Value("${skillama.mail.host:smtp.office365.com}") String host,
            @Value("${skillama.mail.port:587}") int port,
            @Value("${skillama.mail.username:hello@skillama.co.in}") String username,
            @Value("${skillama.mail.password:}") String password) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        mailSender.setProtocol("smtp");
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        return mailSender;
    }
}
