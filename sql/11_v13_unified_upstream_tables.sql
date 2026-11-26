-- =====================================================================
-- 03 组 · 选课及成绩管理 —— 上游公共/外部表统一版建表脚本（DBA 统一字典 v1.3）
-- 文件：11_v13_unified_upstream_tables.sql
-- 依据：系统数据字典统一版v1.3.xlsx（G01 / G02 / G04 / G13 / G15 / DBA 公共表）
-- 用途：
--   1. 在本组独立开发与联调库中，按全班统一数据字典 v1.3 的真实表名、字段名
--      与数据类型建立上游表与跨组视图，确保本组代码可直接平滑对接全班合库；
--   2. 全班正式合库时，由 DBA 直接使用各主维护组的正式表，不重复执行本脚本。
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 一、DBA 公共主数据与统一字典表（4 表）
-- =====================================================================

CREATE TABLE IF NOT EXISTS org_organization (
  org_id        CHAR(6)      NOT NULL COMMENT '组织机构ID',
  org_code      VARCHAR(20)  NOT NULL COMMENT '机构编码',
  org_name      VARCHAR(100) NOT NULL COMMENT '机构名称',
  org_short     VARCHAR(30)  NULL     COMMENT '机构简称',
  org_type      CHAR(2)      NOT NULL COMMENT '机构类型',
  parent_org_id CHAR(6)      NULL     COMMENT '上级机构',
  college_id    CHAR(4)      NULL     COMMENT '学院业务编码（如 0001）',
  leader_emp_id CHAR(10)     NULL     COMMENT '负责人工号',
  contact_phone VARCHAR(20)  NULL     COMMENT '联系电话',
  org_status    CHAR(2)      NOT NULL DEFAULT '01' COMMENT '机构状态',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (org_id),
  UNIQUE KEY uk_org_organization_code (org_code),
  UNIQUE KEY uk_org_organization_college (college_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DBA 统一组织机构表（v1.3）';

CREATE TABLE IF NOT EXISTS sys_person (
  person_id        CHAR(20)     NOT NULL COMMENT '自然人ID',
  person_no        VARCHAR(20)  NULL     COMMENT '学号或工号',
  person_name      VARCHAR(100) NOT NULL COMMENT '姓名',
  person_type      CHAR(2)      NOT NULL COMMENT '人员类别：01学生 02教职工 03交流生',
  gender           CHAR(2)      NOT NULL DEFAULT '9' COMMENT '性别：1男 2女 9未说明',
  id_card_no       CHAR(18)     NULL     COMMENT '身份证号',
  birth_date       DATE         NULL     COMMENT '出生日期',
  ethnicity        VARCHAR(30)  NULL     COMMENT '民族',
  political_status CHAR(2)      NULL     COMMENT '政治面貌',
  phone            VARCHAR(20)  NULL     COMMENT '联系电话',
  home_address     VARCHAR(255) NULL     COMMENT '家庭住址',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at       DATETIME     NULL     COMMENT '更新时间',
  PRIMARY KEY (person_id),
  UNIQUE KEY uk_sys_person_no (person_no),
  UNIQUE KEY uk_sys_person_id_card (id_card_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DBA 统一自然人主索引（v1.3）';

CREATE TABLE IF NOT EXISTS al_dict_type (
  dict_type   VARCHAR(40)  NOT NULL COMMENT '字典类型码',
  type_name   VARCHAR(100) NOT NULL COMMENT '类型名称',
  is_system   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否内置：0否 1是',
  dict_status CHAR(2)      NOT NULL DEFAULT '01' COMMENT '类型状态：01启用 02停用',
  remark      VARCHAR(500) NULL     COMMENT '说明',
  PRIMARY KEY (dict_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DBA 统一字典类型表（v1.3）';

CREATE TABLE IF NOT EXISTS al_dict_item (
  dict_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典项ID',
  dict_type      VARCHAR(40)  NOT NULL COMMENT '字典类型码，关联 al_dict_type.dict_type',
  item_code      VARCHAR(100) NOT NULL COMMENT '码值',
  item_name      VARCHAR(200) NOT NULL COMMENT '码值名称',
  sort_no        INT          NULL     DEFAULT 0 COMMENT '排序号',
  version        VARCHAR(40)  NOT NULL DEFAULT '2026' COMMENT '版本年/备注',
  effective_from DATE         NOT NULL DEFAULT '2026-01-01' COMMENT '生效日期',
  effective_to   DATE         NULL     COMMENT '失效日期',
  item_status    CHAR(2)      NOT NULL DEFAULT '01' COMMENT '字典项状态：01启用 02停用',
  PRIMARY KEY (dict_id),
  UNIQUE KEY uk_al_dict_item_type_code (dict_type, item_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DBA 统一字典项表（v1.3）';

-- =====================================================================
-- 二、第 4 组 师资管理（1 表）
-- =====================================================================

CREATE TABLE IF NOT EXISTS hr_employee (
  emp_id         CHAR(10)      NOT NULL COMMENT '教工号',
  person_id      CHAR(20)      NOT NULL COMMENT '自然人ID，关联 sys_person.person_id',
  emp_name       VARCHAR(50)   NOT NULL COMMENT '姓名',
  id_card        VARCHAR(20)   NOT NULL COMMENT '身份证号',
  phone          VARCHAR(20)   NULL     COMMENT '联系电话',
  org_id         CHAR(6)       NOT NULL COMMENT '所属机构，关联 org_organization.org_id',
  post_type      VARCHAR(20)   NOT NULL DEFAULT 'TEACHER' COMMENT '岗位类别',
  post_level     VARCHAR(20)   NULL     COMMENT '岗位等级',
  title_code     CHAR(40)      NULL     COMMENT '职称代码',
  edu_level      VARCHAR(20)   NULL     COMMENT '最高学历',
  hire_date      DATE          NOT NULL DEFAULT '2020-09-01' COMMENT '入职日期',
  work_date      DATE          NULL     COMMENT '参加工作日期',
  emp_status     CHAR(2)       NOT NULL DEFAULT '01' COMMENT '在职状态',
  salary         DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '工资',
  monthly_salary DECIMAL(18,2) NULL     COMMENT '月工资标准',
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (emp_id),
  UNIQUE KEY uk_hr_employee_id_card (id_card),
  KEY idx_hr_employee_org (org_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第4组 教职工主数据（v1.3）';

-- =====================================================================
-- 三、第 2 组 专业设置及排课（7 表）
-- =====================================================================

CREATE TABLE IF NOT EXISTS aca_term (
  term          CHAR(11)   NOT NULL COMMENT '学年学期，如 2026-2027-1',
  academic_year CHAR(9)    NOT NULL COMMENT '学年，如 2026-2027',
  term_no       TINYINT(1) NOT NULL COMMENT '学期序号：1秋季 2春季',
  start_date    DATE       NOT NULL COMMENT '开始日期',
  end_date      DATE       NOT NULL COMMENT '结束日期',
  term_status   CHAR(2)    NOT NULL DEFAULT '01' COMMENT '学期状态',
  PRIMARY KEY (term)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 学期主数据（v1.3）';

CREATE TABLE IF NOT EXISTS aca_major (
  major_id     CHAR(6)      NOT NULL COMMENT '专业编号',
  major_name   VARCHAR(100) NOT NULL COMMENT '专业名称',
  org_id       CHAR(6)      NOT NULL COMMENT '所属学院 org_id',
  college_id   CHAR(4)      NULL     COMMENT '学院业务编码',
  duration     INT          NOT NULL DEFAULT 4 COMMENT '学制',
  degree_name  VARCHAR(20)  NULL     COMMENT '授予学位',
  min_credit   DECIMAL(5,1) NOT NULL DEFAULT 160.0 COMMENT '毕业最低学分',
  major_status CHAR(2)      NOT NULL DEFAULT '01' COMMENT '专业状态',
  PRIMARY KEY (major_id),
  UNIQUE KEY uk_aca_major_name (major_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 专业主数据（v1.3）';

CREATE TABLE IF NOT EXISTS aca_course (
  course_id     CHAR(10)      NOT NULL COMMENT '课程编号',
  course_code   VARCHAR(20)   NULL     COMMENT '课程显示码',
  course_name   VARCHAR(100)  NOT NULL COMMENT '课程名称',
  course_type   CHAR(2)       NOT NULL COMMENT '课程性质：11校级必修 12院级必修 13专业必修 14专业选修 15通识 16实践',
  credit        DECIMAL(5,1)  NOT NULL COMMENT '学分',
  total_hours   DECIMAL(18,2) NULL     COMMENT '总学时',
  org_id        CHAR(6)       NOT NULL COMMENT '开课学院 org_id',
  course_status CHAR(2)       NOT NULL DEFAULT '01' COMMENT '课程状态',
  PRIMARY KEY (course_id),
  UNIQUE KEY uk_aca_course_code (course_code),
  KEY idx_aca_course_org (org_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 课程主数据（v1.3）';

CREATE TABLE IF NOT EXISTS aca_course_id (
  course_id     CHAR(10)      NOT NULL COMMENT '课程号，关联 aca_course.course_id',
  course_name   VARCHAR(100)  NOT NULL COMMENT '课程名称',
  course_type   CHAR(2)       NOT NULL COMMENT '课程性质',
  credit        DECIMAL(5,1)  NOT NULL COMMENT '学分',
  total_hours   DECIMAL(18,2) NOT NULL DEFAULT 48.00 COMMENT '总学时',
  assess_type   VARCHAR(20)   NOT NULL DEFAULT '1' COMMENT '考核方式：1考试 2考查',
  open_college  VARCHAR(20)   NOT NULL COMMENT '开课学院编码',
  pre_course_id VARCHAR(40)   NULL     COMMENT '先修课程',
  syllabus      VARCHAR(200)  NULL     COMMENT '课程大纲',
  course_status CHAR(2)       NOT NULL DEFAULT '1' COMMENT '课程状态',
  PRIMARY KEY (course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 课程扩展表（v1.3，提供考核方式 assess_type）';

CREATE TABLE IF NOT EXISTS aca_teaching_class (
  tc_id             CHAR(20)      NOT NULL COMMENT '教学班编号',
  term              CHAR(11)      NOT NULL COMMENT '学年学期',
  course_id         CHAR(10)      NOT NULL COMMENT '课程编号',
  emp_id            CHAR(10)      NULL     COMMENT '任课教师工号，关联 hr_employee.emp_id',
  class_name        VARCHAR(50)   NULL     COMMENT '教学班名称',
  room_id           CHAR(12)      NULL     COMMENT '教室',
  capacity          INT           NOT NULL COMMENT '容量人数',
  selected_count    INT           NOT NULL DEFAULT 0 COMMENT '已选人数',
  actual_num        INT           NULL     DEFAULT 0 COMMENT '实际选课人数（第3组回写）',
  class_ids         VARCHAR(100)  NULL     COMMENT '面向班级',
  hours             DECIMAL(18,2) NOT NULL DEFAULT 48.00 COMMENT '学时',
  multiclass_prefer VARCHAR(20)   NOT NULL DEFAULT '2' COMMENT '连排偏好',
  no_open_reason    VARCHAR(100)  NULL     COMMENT '不开课原因',
  open_lower        INT           NULL     DEFAULT 6 COMMENT '开课下限',
  prefer_slot       VARCHAR(20)   NULL     COMMENT '教师偏好节次',
  source            VARCHAR(20)   NOT NULL DEFAULT '1' COMMENT '数据来源',
  student_num       INT           NOT NULL DEFAULT 0 COMMENT '班级人数',
  tc_type           VARCHAR(20)   NOT NULL DEFAULT '1' COMMENT '教学班类型：1必修班 2选修班 3双学位班',
  teach_type        VARCHAR(20)   NOT NULL DEFAULT '1' COMMENT '教学形式',
  tc_status         CHAR(2)       NOT NULL DEFAULT '4' COMMENT '教学班状态：4已发布 2不开课 5取消',
  unavailable_time  VARCHAR(50)   NULL     COMMENT '不可用时间',
  week_count        INT           NOT NULL DEFAULT 16 COMMENT '上课周数',
  PRIMARY KEY (tc_id),
  KEY idx_aca_tc_term (term),
  KEY idx_aca_tc_course (course_id),
  KEY idx_aca_tc_emp (emp_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 教学班主数据（v1.3）';

CREATE TABLE IF NOT EXISTS aca_program_course (
  major_id      CHAR(6)       NOT NULL COMMENT '专业编号',
  grade         VARCHAR(40)   NOT NULL COMMENT '适用年级',
  program_type  VARCHAR(20)   NOT NULL DEFAULT '1' COMMENT '方案类型：1主修 2双学位',
  course_id     CHAR(10)      NOT NULL COMMENT '课程号',
  course_type   CHAR(2)       NOT NULL COMMENT '课程性质',
  credit        DECIMAL(5,1)  NOT NULL COMMENT '学分',
  hours         DECIMAL(18,2) NOT NULL DEFAULT 48.00 COMMENT '学时',
  open_college  VARCHAR(20)   NOT NULL COMMENT '承担学院',
  open_semester INT           NOT NULL DEFAULT 1 COMMENT '开课学期',
  pre_course_id VARCHAR(40)   NULL     COMMENT '先修课程号',
  cross_status  CHAR(2)       NOT NULL DEFAULT '0' COMMENT '跨院确认状态',
  PRIMARY KEY (major_id, grade, program_type, course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 培养方案课程表（v1.3）';

CREATE TABLE IF NOT EXISTS aca_schedule_result (
  sched_id      CHAR(14)     NOT NULL COMMENT '排课编号',
  tc_id         CHAR(20)     NOT NULL COMMENT '教学班编号',
  week_range    VARCHAR(20)  NOT NULL COMMENT '上课周次，如 1-16',
  week_day      VARCHAR(20)  NOT NULL COMMENT '星期：1周一..7周日',
  start_slot    INT          NOT NULL COMMENT '开始节次',
  end_slot      INT          NOT NULL COMMENT '结束节次',
  room_id       CHAR(12)     NOT NULL COMMENT '教室编号',
  lock_flag     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '锁定标志',
  sched_status  CHAR(2)      NOT NULL DEFAULT '0' COMMENT '排课状态',
  conflict_desc VARCHAR(200) NULL     COMMENT '冲突说明',
  PRIMARY KEY (sched_id),
  KEY idx_aca_sched_tc (tc_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 排课结果表（v1.3）';

-- =====================================================================
-- 四、第 1 组 学籍管理（2 表）
-- =====================================================================

CREATE TABLE IF NOT EXISTS aca_class (
  class_id         CHAR(10)    NOT NULL COMMENT '班级编号',
  class_name       VARCHAR(50) NOT NULL COMMENT '班级名称',
  grade            VARCHAR(40) NOT NULL COMMENT '年级',
  major_id         CHAR(6)     NOT NULL COMMENT '专业编号',
  org_id           CHAR(6)     NULL     COMMENT '所属学院 org_id',
  counselor_emp_id CHAR(10)    NULL     COMMENT '辅导员工号',
  PRIMARY KEY (class_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第1组 行政班主数据（v1.3）';

CREATE TABLE IF NOT EXISTS std_student (
  student_id       CHAR(20)    NOT NULL COMMENT '学号',
  person_id        CHAR(20)    NOT NULL COMMENT '自然人ID，关联 sys_person.person_id',
  class_id         CHAR(10)    NULL     COMMENT '行政班，关联 aca_class.class_id',
  major_id         CHAR(6)     NULL     COMMENT '专业编号，关联 aca_major.major_id',
  org_id           CHAR(6)     NOT NULL COMMENT '所属学院，关联 org_organization.org_id',
  grade            VARCHAR(40) NULL     COMMENT '年级（如 2024）',
  enrollment_year  INT         NOT NULL COMMENT '入学年份',
  enrollment_date  DATE        NULL     COMMENT '入学日期',
  graduation_date  DATE        NULL     COMMENT '毕业日期',
  student_type     CHAR(2)     NOT NULL COMMENT '学生类别：01本科 02硕士 03博士 04交流生',
  student_status   CHAR(2)     NOT NULL DEFAULT '01' COMMENT '学籍状态：01在籍',
  counselor_emp_id CHAR(10)    NULL     COMMENT '辅导员工号',
  dorm_id          CHAR(12)    NULL     COMMENT '宿舍',
  created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (student_id),
  KEY idx_std_student_person (person_id),
  KEY idx_std_student_major_grade (major_id, grade)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第1组 学生主数据（v1.3）';

-- =====================================================================
-- 五、第 13 组 研究生管理（3 表）
-- =====================================================================

CREATE TABLE IF NOT EXISTS grad_direction (
  direction_id   VARCHAR(20) NOT NULL COMMENT '研究方向编号',
  direction_name VARCHAR(50) NOT NULL COMMENT '研究方向名称',
  PRIMARY KEY (direction_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第13组 研究方向表（v1.3）';

CREATE TABLE IF NOT EXISTS grad_scheme_assignment (
  assignment_id VARCHAR(50) NOT NULL COMMENT '指定标识',
  student_id    CHAR(20)    NOT NULL COMMENT '学生标识',
  scheme_id     VARCHAR(50) NOT NULL COMMENT '方案版本标识',
  direction_id  VARCHAR(20) NULL     COMMENT '研究方向编号',
  method        VARCHAR(50) NOT NULL DEFAULT 'DEFAULT' COMMENT '指定方式',
  valid_from    DATE        NOT NULL COMMENT '开始日期（原 effective_from）',
  valid_to      DATE        NULL     COMMENT '结束日期（原 effective_to）',
  PRIMARY KEY (assignment_id),
  KEY idx_grad_assign_student (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第13组 研究生方案指定表（v1.3）';

CREATE TABLE IF NOT EXISTS grad_scheme_course (
  scheme_course_id VARCHAR(50) NOT NULL COMMENT '方案课程标识',
  scheme_id        VARCHAR(50) NOT NULL COMMENT '方案标识',
  course_id        CHAR(10)    NOT NULL COMMENT '课程标识',
  direction_id     VARCHAR(20) NULL     COMMENT '研究方向编号',
  group_code       VARCHAR(50) NOT NULL DEFAULT 'CORE' COMMENT '课程分类代码',
  course_category  INT         NOT NULL COMMENT '课程类别：1年度必修 2方向必修 3方向选修',
  suggest_term     INT         NULL     COMMENT '建议修读学期',
  PRIMARY KEY (scheme_course_id),
  KEY idx_grad_scheme_course (scheme_id, course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第13组 研究生方案课程表（v1.3）';

-- =====================================================================
-- 六、第 15 组 对外交流管理（6 表 + 2 跨组冻结只读视图）
-- =====================================================================

CREATE TABLE IF NOT EXISTS exg_application (
  apply_id           VARCHAR(40)  NOT NULL COMMENT '申请编号',
  person_id          CHAR(20)     NOT NULL COMMENT '自然人ID',
  student_name       VARCHAR(50)  NOT NULL COMMENT '学生姓名',
  id_card_no         CHAR(18)     NOT NULL COMMENT '身份证号',
  home_school        VARCHAR(100) NOT NULL COMMENT '派出学校',
  home_student_no    VARCHAR(20)  NOT NULL COMMENT '派出校学号',
  apply_college_name VARCHAR(100) NOT NULL COMMENT '申请学院',
  apply_major_name   VARCHAR(100) NOT NULL COMMENT '申请专业',
  apply_term         VARCHAR(20)  NOT NULL COMMENT '申请学期',
  contact_email      VARCHAR(100) NULL     COMMENT '联系邮箱',
  apply_status       CHAR(2)      NOT NULL DEFAULT '01' COMMENT '申请状态',
  submitted_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (apply_id),
  UNIQUE KEY uk_exg_app_home_no (home_student_no),
  UNIQUE KEY uk_exg_app_id_card (id_card_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 来校交流生申请表（v1.3）';

CREATE TABLE IF NOT EXISTS exg_admission (
  admission_id     CHAR(40)     NOT NULL COMMENT '录取编号',
  apply_id         VARCHAR(40)  NOT NULL COMMENT '关联申请编号',
  person_id        CHAR(20)     NOT NULL COMMENT '自然人ID',
  student_name     VARCHAR(50)  NOT NULL COMMENT '学生姓名',
  college_name     VARCHAR(100) NOT NULL COMMENT '录取学院',
  major_name       VARCHAR(100) NOT NULL COMMENT '录取专业',
  term             CHAR(11)     NOT NULL COMMENT '交流学期',
  interview_score  DECIMAL(5,2) NOT NULL DEFAULT 90.00 COMMENT '面试成绩',
  approver_name    VARCHAR(50)  NOT NULL DEFAULT '教务处' COMMENT '审批人',
  admission_time   DATE         NOT NULL COMMENT '录取时间',
  admission_status CHAR(2)      NOT NULL DEFAULT '01' COMMENT '录取状态',
  PRIMARY KEY (admission_id),
  UNIQUE KEY uk_exg_adm_apply (apply_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 来校交流生录取表（v1.3）';

CREATE TABLE IF NOT EXISTS exg_temp_student (
  temp_student_id CHAR(20)     NOT NULL COMMENT '临时学号',
  admission_id    CHAR(40)     NOT NULL COMMENT '关联录取编号',
  person_id       CHAR(20)     NOT NULL COMMENT '自然人ID',
  student_name    VARCHAR(50)  NOT NULL COMMENT '学生姓名',
  id_card_no      CHAR(18)     NOT NULL COMMENT '身份证号',
  org_id          CHAR(6)      NOT NULL COMMENT '接收学院 org_id',
  college_name    VARCHAR(100) NOT NULL COMMENT '所属学院',
  major_name      VARCHAR(100) NOT NULL COMMENT '所学专业',
  start_date      DATE         NOT NULL COMMENT '交流开始日期',
  end_date        DATE         NOT NULL COMMENT '交流结束日期',
  student_status  CHAR(2)      NOT NULL DEFAULT '01' COMMENT '学籍状态：01在读 02已结业',
  creator_name    VARCHAR(50)  NOT NULL DEFAULT '国际处' COMMENT '创建人',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (temp_student_id),
  UNIQUE KEY uk_exg_temp_adm (admission_id),
  UNIQUE KEY uk_exg_temp_id_card (id_card_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 来校交流生临时学籍表（v1.3）';

CREATE TABLE IF NOT EXISTS exg_vst_departure (
  departure_id        CHAR(20)     NOT NULL COMMENT '出境登记编号',
  apply_id            VARCHAR(40)  NOT NULL COMMENT '申请编号',
  student_id          CHAR(20)     NOT NULL COMMENT '学号',
  host_country        VARCHAR(50)  NOT NULL COMMENT '目的地国家或地区',
  partner_school      VARCHAR(100) NOT NULL COMMENT '合作院校',
  departure_date      DATE         NOT NULL COMMENT '出境日期',
  planned_return_date DATE         NOT NULL COMMENT '计划返校日期',
  actual_return_date  DATE         NULL     COMMENT '实际返校日期',
  return_status       CHAR(2)      NOT NULL DEFAULT '01' COMMENT '返校状态',
  PRIMARY KEY (departure_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 外出交流出境登记表（v1.3）';

CREATE TABLE IF NOT EXISTS exg_vst_return_report (
  report_id               VARCHAR(50)  NOT NULL COMMENT '报到编号',
  departure_id            CHAR(20)     NOT NULL COMMENT '出境登记编号',
  student_id              CHAR(20)     NOT NULL COMMENT '学号',
  actual_return_date      DATE         NOT NULL COMMENT '实际返校日期',
  report_date             DATE         NOT NULL COMMENT '报到日期',
  return_apply_file       VARCHAR(255) NOT NULL DEFAULT 'return_apply.pdf' COMMENT '返校报到申请表',
  partner_transcript_file DECIMAL(5,2) NULL     COMMENT '合作院校成绩单标记',
  report_note             VARCHAR(500) NULL     COMMENT '报到说明',
  PRIMARY KEY (report_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 外出交流返校报到表（v1.3）';

CREATE TABLE IF NOT EXISTS exg_vst_return_filing (
  return_filing_id   CHAR(20)    NOT NULL COMMENT '返校备案编号',
  report_id          VARCHAR(50) NOT NULL COMMENT '报到编号',
  student_id         CHAR(20)    NOT NULL COMMENT '学号',
  actual_return_date DATE        NOT NULL COMMENT '实际返校日期',
  filing_date        DATE        NOT NULL COMMENT '备案日期',
  filer              VARCHAR(50) NOT NULL DEFAULT '国际处' COMMENT '备案人',
  filing_status      CHAR(2)     NOT NULL DEFAULT '01' COMMENT '备案状态：01已备案 00待备案',
  PRIMARY KEY (return_filing_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 外出交流返校备案表（v1.3）';

-- 跨组冻结只读接口视图（基于第 15 组 v1.3 正式表投影，保持第 3 组与第 15 组冻结视图口径）
CREATE OR REPLACE VIEW v_exchange_student AS
SELECT ts.temp_student_id AS temp_student_no,
       ts.student_name,
       app.home_school AS sender_school_name,
       adm.term AS term_ref,
       COALESCE(org.college_id, '0001') AS college_ref,
       COALESCE(m.major_id, '120102') AS major_ref,
       ts.start_date AS start_on,
       ts.end_date AS end_on,
       CASE WHEN ts.student_status IN ('01', '1') THEN 'ACTIVE' ELSE 'FINISHED' END AS status_code
FROM exg_temp_student ts
JOIN exg_admission adm ON ts.admission_id = adm.admission_id
JOIN exg_application app ON adm.apply_id = app.apply_id
LEFT JOIN org_organization org ON ts.org_id = org.org_id
LEFT JOIN aca_major m ON ts.major_name = m.major_name;

CREATE OR REPLACE VIEW v_exchange_credit_ready AS
SELECT d.apply_id AS application_no,
       f.student_id,
       COALESCE(
         (SELECT t.term FROM aca_term t WHERE d.departure_date BETWEEN t.start_date AND t.end_date LIMIT 1),
         '2026-2027-1'
       ) AS term_ref,
       d.partner_school AS partner_school_snapshot,
       CASE WHEN f.filing_status = '01' THEN 1 ELSE 0 END AS ready_flag
FROM exg_vst_return_filing f
JOIN exg_vst_return_report r ON f.report_id = r.report_id
JOIN exg_vst_departure d ON r.departure_id = d.departure_id;

-- =====================================================================
-- 七、本组业务⑥ 6.2 数据备份与恢复演示日志表（非跨组表，保留支撑 6.2）
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_backup_log (
  backup_id    BIGINT       NOT NULL AUTO_INCREMENT,
  backup_type  TINYINT      NOT NULL COMMENT '1手工备份 2恢复前应急快照',
  file_name    VARCHAR(150) NOT NULL COMMENT '备份文件名',
  file_size_kb INT          NULL,
  operator_id  VARCHAR(20)  NOT NULL,
  backup_time  DATETIME     NOT NULL,
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 2失败',
  remark       VARCHAR(200) NULL,
  PRIMARY KEY (backup_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据备份与恢复日志表（业务⑥ 6.2 本地演示表）';
