package com.prwatech.courses.service.impl;

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfWriter;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.courses.model.CourseDetails;
import com.prwatech.courses.model.CourseTrack;
import com.prwatech.courses.repository.CourseDetailRepository;
import com.prwatech.courses.repository.CourseTrackTemplate;
import com.prwatech.courses.service.FileServices;
import com.prwatech.user.model.User;
import com.prwatech.user.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;


@AllArgsConstructor
@Component
public class FileServiceImpl implements FileServices {


    private final UserRepository userRepository;
    private final CourseDetailRepository courseDetailRepository;
    private final SpringTemplateEngine templateEngine;

    private final CourseTrackTemplate courseTrackTemplate;
    private final Logger LOGGER = LoggerFactory.getLogger(FileServiceImpl.class);


    @Override
    public ByteArrayInputStream generateCertificateByUserIdAndCourseId(String userId, String courseId) {

        User user = userRepository.findById(userId).orElseThrow(()-> new NotFoundException("User not found by given id."));

        if(user.getName()==null){
            throw new UnProcessableEntityException("Please complete your profile details.");
        }

        CourseDetails courseDetails = courseDetailRepository.findById(courseId).orElseThrow(()-> new NotFoundException("No course found by this course id"));

        CourseTrack courseTrack = courseTrackTemplate.getByCourseIdAndUserId(new ObjectId(userId), new ObjectId(courseId));

        if (Objects.isNull(courseTrack)){
            throw new NotFoundException("You have not bought this course.");
        }

        if(courseTrack.getIsAllCompleted()){
            throw new UnProcessableEntityException("You must complete the course to get certificate.");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd, MMMM, yyyy", Locale.ENGLISH);
        String date= courseTrack.getUpdatedAt().format(formatter);
        LOGGER.info("Creating the html template for certificate.");
        String template=parseThymeleafTemplate(user.getName(), courseDetails.getCourse_Title(), date);

        LOGGER.info("Creating the pdf template for certificate.");
        return generatePdfFromHtml(template);

    }

    private String parseThymeleafTemplate( String name, String courseName, String date) {


        try{
            Image logo = Image.getInstance(getClass().getResource("/templates/logo.jpeg"));
            Image sign = Image.getInstance(getClass().getResource("/templates/sign.jpeg"));
        }
        catch (Exception e){
            LOGGER.error("Can not read the imaged from db to file.");
            return null;
        }
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("courseName", courseName);
        context.setVariable("dateOfCompletion", date);

        return templateEngine.process("certificate" , context);
    }

    public ByteArrayInputStream generatePdfFromHtml(String html) {
        try
        {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(outputStream);

            outputStream.close();

            byte[] pdfBytes = outputStream.toByteArray();
            return new ByteArrayInputStream(pdfBytes);
        }
        catch (Exception e){
            LOGGER.error("Something went wrong while writing the file into pdf!");
        }

        return null;
    }

}
