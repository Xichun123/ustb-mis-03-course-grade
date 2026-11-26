package com.ustb.coursegrade.module.selection;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 课程类型直接对齐第 2 组 aca_course.course_type（11–16 六类）；教学班号可附带已公布的特殊要求。 */
@ConfigurationProperties("selection.catalog")
public record SelectionCatalogProperties(Map<String, CourseType> courseTypes,
                                         Map<String, String> specialRequirements) {
    public SelectionCatalogProperties {
        courseTypes = courseTypes == null ? Map.of() : Map.copyOf(courseTypes);
        specialRequirements = specialRequirements == null ? Map.of() : Map.copyOf(specialRequirements);
    }

    CourseType courseType(String courseId, String code) {
        CourseType override = courseTypes.get(courseId);
        if (override != null && override != CourseType.UNCLASSIFIED) {
            return override;
        }
        return CourseType.fromCode(code);
    }

    public enum CourseType {
        UNIVERSITY_REQUIRED("11", "校级必修"),
        COLLEGE_REQUIRED("12", "院级必修"),
        MAJOR_REQUIRED("13", "专业必修"),
        MAJOR_ELECTIVE("14", "专业选修"),
        GENERAL_ELECTIVE("15", "通识选修"),
        PRACTICE("16", "实践环节"),
        UNCLASSIFIED("", "其他");

        private final String code;
        private final String label;

        CourseType(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String code() { return code; }

        public String label() { return label; }

        public static CourseType fromCode(String code) {
            String normalized = code == null ? "" : code.trim();
            return switch (normalized) {
                case "11" -> UNIVERSITY_REQUIRED;
                case "12" -> COLLEGE_REQUIRED;
                case "13" -> MAJOR_REQUIRED;
                case "14" -> MAJOR_ELECTIVE;
                case "15" -> GENERAL_ELECTIVE;
                case "16" -> PRACTICE;
                default -> UNCLASSIFIED;
            };
        }

        public static CourseType fromParam(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String normalized = raw.trim();
            for (CourseType t : values()) {
                if (t == UNCLASSIFIED) continue;
                if (t.code.equals(normalized) || t.name().equalsIgnoreCase(normalized)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("无效课程类型：" + raw);
        }

        static List<Map<String, String>> options() {
            return Arrays.stream(values())
                    .filter(type -> type != UNCLASSIFIED)
                    .map(type -> Map.of("value", type.code, "label", type.label))
                    .toList();
        }
    }
}
