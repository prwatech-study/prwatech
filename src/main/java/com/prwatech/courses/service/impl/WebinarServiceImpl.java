package com.prwatech.courses.service.impl;

import com.prwatech.common.Constants;
import com.prwatech.common.dto.PaginationDto;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.common.utility.Utility;
import com.prwatech.courses.dto.RegisterWebinarRequestDto;
import com.prwatech.courses.model.Webinar;
import com.prwatech.courses.model.WebinarRegister;
import com.prwatech.courses.repository.WebinarRegisterRepository;
import com.prwatech.courses.repository.WebinarRepository;
import com.prwatech.courses.repository.WebinarTemplate;
import com.prwatech.courses.service.WebinarService;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


@Service
@AllArgsConstructor
public class WebinarServiceImpl implements WebinarService {

    private final WebinarTemplate webinarTemplate;
    private final WebinarRepository webinarRepository;
    private final WebinarRegisterRepository webinarRegisterRepository;
    private final static Logger LOGGER = LoggerFactory.getLogger(WebinarServiceImpl.class);

    @Override
    public PaginationDto getAllWebinar(Integer pageNumber, Integer pageSize) {

        Page<Webinar> webinarPage = webinarTemplate.getAllWebinar(pageNumber, pageSize);
        List<Webinar> webinarList = null;
        if(!webinarPage.isEmpty()){
             webinarList = webinarPage.getContent();
             webinarList.forEach(webinar -> webinar.setWebinar_Image(Constants.WEBINAR_IMAGE));
        }

        return Utility.getPaginatedResponse(
                Collections.singletonList(webinarList),
                webinarPage.getPageable(),
                webinarPage.getTotalPages(),
                webinarPage.hasNext(),
                webinarPage.getTotalElements()
        );
    }

    @Override
    public Webinar getWebinarById(String id) {

        Webinar webinar = webinarRepository.findById(id).orElseThrow(
                ()-> new NotFoundException("No Webinar or event found by this Id : "+ id)
        );
        return webinar;
    }

    @Override
    public void registerWebinar(RegisterWebinarRequestDto webinarRequestDto) {

        if(webinarRequestDto.getWebinarId()==null){
            LOGGER.error("Registering for webinar :: Webinar id is null.");
            throw new UnProcessableEntityException("Webinar Id can not be null! ");
        }
        if(webinarRequestDto.getEmail()==null){
            LOGGER.error("Registering for webinar :: email is null.");
            throw new UnProcessableEntityException("email can not be null! ");
        }

        Webinar webinar = webinarRepository.findById(webinarRequestDto.getWebinarId()).orElseThrow(
                ()-> new NotFoundException("No webinar found by this id : "+ webinarRequestDto.getWebinarId())
        );

        WebinarRegister webinarRegister = new WebinarRegister();
        webinarRegister.setWebinar_Id(new ObjectId(webinarRequestDto.getWebinarId()));
        webinarRegister.setEmail(webinarRegister.getEmail());
        webinarRegister.setName((webinarRegister.getName()!=null)? webinarRegister.getName() : null);
        webinarRegister.setPhone_Number((webinarRegister.getPhone_Number()!=null)? webinarRegister.getPhone_Number() : null);

        LOGGER.info("Registering to webinar for this user.");
        webinarRegisterRepository.save(webinarRegister);
    }

    @Override
    public List<Webinar> getAllRegisteredWebinar(String email) {

        List<WebinarRegister> webinarRegisterList = webinarTemplate.getAllRegisteredWebinar(email);
        List<Webinar> webinarList = new ArrayList<>();
        for(WebinarRegister webinarRegister : webinarRegisterList){
            Optional<Webinar> webinarOp = webinarRepository.findById(webinarRegister.getWebinar_Id().toString());
            if(webinarOp.isPresent()){
                webinarList.add(webinarOp.get());
            }else
            {
                LOGGER.error("This webinar has been removed! with id: {}", webinarRegister.getWebinar_Id());
            }
        }
        return webinarList;
    }


}
