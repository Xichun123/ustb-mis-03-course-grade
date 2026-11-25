package com.ustb.coursegrade.module.selection;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 课程号对应分类，教学班号对应已公布的特殊要求。两项均用于展示。 */
@ConfigurationProperties("selection.catalog")
public record SelectionCatalogProperties(Map<String, CourseType> courseTypes,
                                         Map<String, String> specialRequirements) {
    public SelectionCatalogProperties {
        courseTypes = courseTypes == null ? Map.of() : Map.copyOf(courseTypes);
        specialRequirements = specialRequirements == null ? Map.of() : Map.copyOf(specialRequirements);
    }

    CourseType courseType(String courseId, String code) {
        String normalized = code == null ? "" : code.trim();
        return courseTypes.getOrDefault(courseId, switch (normalized) {
            case "11", "12", "13" -> CourseType.REQUIRED;
            case "14" -> CourseType.PROFESSIONAL_EXTENSION;
            case "15" -> CourseType.GENERAL_ELECTIVE;
            case "16" -> CourseType.PRACTICE;
            default -> CourseType.UNCLASSIFIED;
        });
    }

    public enum CourseType {
        REQUIRED("必修"),
        PROFESSIONAL_EXTENSION("专业拓展课"),
        SCIENCE_LITERACY("素质拓展课-科学素养"),
        FOREIGN_LANGUAGE("素质拓展课-外语"),
        AESTHETICS("素质拓展课-美育"),
        HUMANITIES("素质拓展课-人文素养"),
        INNOVATION_ENTREPRENEURSHIP("素质拓展课-创新创业课程"),
        GENERAL_ELECTIVE("通识选修"),
        PUBLIC_ELECTIVE("公共选修"),
        PRACTICE("实践"),
        UNCLASSIFIED("课程类型待确认");

        private final String label;

        CourseType(String label) { this.label = label; }

        String label() { return label; }

        static List<Map<String, String>> options() {
            return Arrays.stream(values()).map(type -> Map.of("value", type.name(), "label", type.label)).toList();
        }
    }
}
