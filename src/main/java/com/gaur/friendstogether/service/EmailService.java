package com.gaur.friendstogether.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("🔐 Verify Your FriendsTogether Account");

            // Attractive HTML Design
            String htmlContent = """
                <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 600px; margin: auto; border: 1px solid #e0e0e0; border-radius: 12px; overflow: hidden;">
                    <div style="background-color: #6366f1; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0; font-size: 24px;">FriendsTogether</h1>
                    </div>
                    <div style="padding: 40px; text-align: center; background-color: #ffffff;">
                        <h2 style="color: #333;">Welcome to the Club!</h2>
                        <p style="color: #666; font-size: 16px;">Use the verification code below to complete your registration.</p>
                        <div style="margin: 30px 0;">
                            <span style="font-size: 36px; font-weight: bold; letter-spacing: 5px; color: #6366f1; background: #f0f4ff; padding: 10px 30px; border-radius: 8px; border: 2px dashed #6366f1;">
                                %s
                            </span>
                        </div>
                        <p style="color: #999; font-size: 13px;">This code will expire in <b>10 minutes</b>. If you didn't request this, please ignore this email.</p>
                    </div>
                    <div style="background-color: #f9fafb; padding: 20px; text-align: center; color: #9ca3af; font-size: 12px;">
                        &copy; 2026 FriendsTogether. Built with ❤️ for Developers.
                    </div>
                </div>
                """.formatted(otp);

            helper.setText(htmlContent, true); // 'true' means it's HTML
            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send attractive email", e);
        }
    }
}