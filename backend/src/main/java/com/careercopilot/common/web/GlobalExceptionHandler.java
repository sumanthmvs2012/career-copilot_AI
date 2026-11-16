package com.careercopilot.common.web;

import com.careercopilot.applications.ApplicationAlreadyExistsException;
import com.careercopilot.applications.ApplicationNotDraftException;
import com.careercopilot.applications.ApplicationNotFoundException;
import com.careercopilot.auth.EmailAlreadyUsedException;
import com.careercopilot.auth.InvalidCredentialsException;
import com.careercopilot.auth.PasswordTooLongException;
import com.careercopilot.jobs.InvalidJobQueryException;
import com.careercopilot.mail.MailAlreadyConnectedException;
import com.careercopilot.mail.MailConnectionNotFoundException;
import com.careercopilot.mail.MailNotConfiguredException;
import com.careercopilot.mail.MailNotConnectedException;
import com.careercopilot.mail.MailProviderException;
import com.careercopilot.mail.ReminderNotFoundException;
import com.careercopilot.matches.JobNotFoundException;
import com.careercopilot.matches.MatchNotFoundException;
import com.careercopilot.matches.NoBaseResumeException;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.resumes.InvalidResumeFileException;
import com.careercopilot.resumes.ResumeLimitReachedException;
import com.careercopilot.resumes.ResumeNotFoundException;
import com.careercopilot.resumes.ResumeTooLargeException;
import com.careercopilot.resumes.ResumeUnreadableException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns every error into an ErrorResponse; technical details go to the logs, never the client. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        // Only the field name and our own message are returned; rejected values (passwords) are not.
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return respond(HttpStatus.BAD_REQUEST, new ErrorResponse(400, "Please check your details.", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, new ErrorResponse(400, "Request body is missing or malformed."));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> handleBadParameter(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, new ErrorResponse(400, "A request parameter is missing or invalid."));
    }

    @ExceptionHandler(PasswordTooLongException.class)
    ResponseEntity<ErrorResponse> handlePasswordTooLong(PasswordTooLongException ex) {
        return respond(HttpStatus.BAD_REQUEST,
                new ErrorResponse(400, "Please check your details.", Map.of("password", ex.getMessage())));
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    ResponseEntity<ErrorResponse> handleEmailUsed(EmailAlreadyUsedException ex) {
        return respond(HttpStatus.CONFLICT, new ErrorResponse(409, "An account with this email already exists."));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return respond(HttpStatus.UNAUTHORIZED, new ErrorResponse(401, "Email or password is incorrect."));
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    ResponseEntity<ErrorResponse> handleProfileNotFound(ProfileNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, new ErrorResponse(404, "Create your profile first."));
    }

    @ExceptionHandler(InvalidJobQueryException.class)
    ResponseEntity<ErrorResponse> handleInvalidJobQuery(InvalidJobQueryException ex) {
        return respond(HttpStatus.BAD_REQUEST, new ErrorResponse(400, ex.getMessage()));
    }

    @ExceptionHandler(ResumeNotFoundException.class)
    ResponseEntity<ErrorResponse> handleResumeNotFound(ResumeNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, new ErrorResponse(404, "Resume not found."));
    }

    @ExceptionHandler(ResumeLimitReachedException.class)
    ResponseEntity<ErrorResponse> handleResumeLimit(ResumeLimitReachedException ex) {
        return respond(HttpStatus.CONFLICT,
                new ErrorResponse(409, "You already have 2 resumes. Delete one to upload another."));
    }

    @ExceptionHandler(InvalidResumeFileException.class)
    ResponseEntity<ErrorResponse> handleInvalidResumeFile(InvalidResumeFileException ex) {
        return respond(HttpStatus.BAD_REQUEST, new ErrorResponse(400, "Upload a PDF or DOCX file."));
    }

    @ExceptionHandler({ResumeTooLargeException.class, MaxUploadSizeExceededException.class})
    ResponseEntity<ErrorResponse> handleTooLarge(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, new ErrorResponse(400, "The file must be 5 MB or smaller."));
    }

    @ExceptionHandler(ResumeUnreadableException.class)
    ResponseEntity<ErrorResponse> handleResumeUnreadable(ResumeUnreadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, new ErrorResponse(400, "We could not read text from this file."));
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    ResponseEntity<ErrorResponse> handleApplicationNotFound(ApplicationNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, new ErrorResponse(404, "Application or job not found."));
    }

    @ExceptionHandler(ApplicationAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> handleApplicationExists(ApplicationAlreadyExistsException ex) {
        return respond(HttpStatus.CONFLICT, new ErrorResponse(409, "You already track this job."));
    }

    @ExceptionHandler(ApplicationNotDraftException.class)
    ResponseEntity<ErrorResponse> handleNotDraft(ApplicationNotDraftException ex) {
        return respond(HttpStatus.CONFLICT, new ErrorResponse(409, "Only a draft can be submitted."));
    }

    @ExceptionHandler(MailNotConfiguredException.class)
    ResponseEntity<ErrorResponse> handleMailNotConfigured(MailNotConfiguredException ex) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE,
                new ErrorResponse(503, "Mailbox connection is not configured."));
    }

    @ExceptionHandler(MailAlreadyConnectedException.class)
    ResponseEntity<ErrorResponse> handleMailAlreadyConnected(MailAlreadyConnectedException ex) {
        return respond(HttpStatus.CONFLICT, new ErrorResponse(409, "Your mailbox is already connected."));
    }

    @ExceptionHandler(MailNotConnectedException.class)
    ResponseEntity<ErrorResponse> handleMailNotConnected(MailNotConnectedException ex) {
        return respond(HttpStatus.CONFLICT, new ErrorResponse(409, "Connect your mailbox first."));
    }

    @ExceptionHandler(MailConnectionNotFoundException.class)
    ResponseEntity<ErrorResponse> handleMailConnectionNotFound(MailConnectionNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, new ErrorResponse(404, "Mail connection not found."));
    }

    @ExceptionHandler(ReminderNotFoundException.class)
    ResponseEntity<ErrorResponse> handleReminderNotFound(ReminderNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, new ErrorResponse(404, "Reminder not found."));
    }

    @ExceptionHandler(MailProviderException.class)
    ResponseEntity<ErrorResponse> handleMailProvider(MailProviderException ex) {
        // The message is ours and carries no provider details; only the fixed text goes to the client.
        log.warn("Mail provider call failed: {}", ex.getMessage());
        return respond(HttpStatus.BAD_GATEWAY,
                new ErrorResponse(502, "Could not reach your mailbox. Try again later."));
    }

    @ExceptionHandler(JobNotFoundException.class)
    ResponseEntity<ErrorResponse> handleJobNotFound(JobNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, new ErrorResponse(404, "Job not found."));
    }

    @ExceptionHandler(MatchNotFoundException.class)
    ResponseEntity<ErrorResponse> handleMatchNotFound(MatchNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, new ErrorResponse(404, "No match yet for this job."));
    }

    @ExceptionHandler(NoBaseResumeException.class)
    ResponseEntity<ErrorResponse> handleNoBaseResume(NoBaseResumeException ex) {
        return respond(HttpStatus.CONFLICT, new ErrorResponse(409, "Upload a resume first to see your match."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, new ErrorResponse(404, "Not found."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, new ErrorResponse(405, "Method not allowed."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                new ErrorResponse(500, "Something went wrong. Please try again."));
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorResponse body) {
        return ResponseEntity.status(status).body(body);
    }
}
