package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Public curriculum outline for the course detail page — labels only, no scripts. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOutlineModuleDTO {
    private String moduleName;
    private List<String> lectures;
}
