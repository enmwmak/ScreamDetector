package edu.polyu.utils;

import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class GMailSender extends javax.mail.Authenticator {
	private Properties props;
	private Session session;
	
	public GMailSender(final String user, final String password) {
		props = new Properties();
		props.put("mail.smtp.host", "smtp.gmail.com");
		props.put("mail.smtp.socketFactory.port", "465");
		props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.port", "465");
		session = Session.getDefaultInstance(props, new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(user, password);
			}
		});
	}

	/*
	 * Send the email in another thread to avoid NetworkOnMainThreadException
	 */
	public synchronized void sendMail(final String subject, final String body, final String sender, 
										final String recipient) throws Exception {
		Thread mailThread = new Thread(new Runnable() {
			@Override
			public void run() {
				session.setDebug(true);
				try {
					Message message = new MimeMessage(session);
					message.setFrom(new InternetAddress(sender));
					message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
					message.setSubject(subject);
					message.setText(body);
					System.out.println("GMailSender2: sending message");
					Transport.send(message);
					System.out.println("GMailSender2: Done");
				} catch (MessagingException e) {
				}
			}
		});
		mailThread.start();
	}

	public class ByteArrayDataSource implements DataSource {
		private byte[] data;
		private String type;

		public ByteArrayDataSource(byte[] data, String type) {
			super();
			this.data = data;
			this.type = type;
		}

		public ByteArrayDataSource(byte[] data) {
			super();
			this.data = data;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getContentType() {
			if (type == null)
				return "application/octet-stream";
			else
				return type;
		}

		public InputStream getInputStream() throws IOException {
			return new ByteArrayInputStream(data);
		}

		public String getName() {
			return "ByteArrayDataSource";
		}

		public OutputStream getOutputStream() throws IOException {
			throw new IOException("Not Supported");
		}
	}
}
