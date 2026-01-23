package com.billboard.social.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@billboard.com}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;

    @Async
    public void sendGroupInvitationEmail(String toEmail, String inviterName, String groupName,
                                         String inviteCode, String message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("You're invited to join " + groupName);

            String inviteLink = baseUrl + "/invite/" + inviteCode;

            String htmlContent = buildInvitationEmailHtml(inviterName, groupName, message, inviteLink);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("Invitation email sent to {} for group {}", toEmail, groupName);

        } catch (MessagingException e) {
            log.error("Failed to send invitation email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildInvitationEmailHtml(String inviterName, String groupName,
                                            String message, String inviteLink) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }");
        html.append(".container { max-width: 600px; margin: 0 auto; padding: 20px; }");
        html.append(".header { background: #4F46E5; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }");
        html.append(".content { background: #f9fafb; padding: 30px; border-radius: 0 0 8px 8px; }");
        html.append(".button { display: inline-block; background: #4F46E5; color: white; padding: 12px 30px; ");
        html.append("text-decoration: none; border-radius: 6px; margin: 20px 0; }");
        html.append(".message { background: white; padding: 15px; border-left: 4px solid #4F46E5; margin: 20px 0; }");
        html.append(".footer { text-align: center; color: #666; font-size: 12px; margin-top: 20px; }");
        html.append("</style></head><body>");

        html.append("<div class='container'>");
        html.append("<div class='header'>");
        html.append("<h1>You're Invited! 🎉</h1>");
        html.append("</div>");

        html.append("<div class='content'>");
        html.append("<p>Hi there,</p>");
        html.append("<p><strong>").append(escapeHtml(inviterName)).append("</strong> has invited you to join ");
        html.append("<strong>").append(escapeHtml(groupName)).append("</strong>.</p>");

        if (message != null && !message.isBlank()) {
            html.append("<div class='message'>");
            html.append("<p><em>\"").append(escapeHtml(message)).append("\"</em></p>");
            html.append("</div>");
        }

        html.append("<p style='text-align: center;'>");
        html.append("<a href='").append(inviteLink).append("' class='button'>Accept Invitation</a>");
        html.append("</p>");

        html.append("<p>Or copy this link: <br>");
        html.append("<code>").append(inviteLink).append("</code></p>");

        html.append("<p>This invitation will expire in 7 days.</p>");
        html.append("</div>");

        html.append("<div class='footer'>");
        html.append("<p>If you didn't expect this invitation, you can safely ignore this email.</p>");
        html.append("</div>");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}