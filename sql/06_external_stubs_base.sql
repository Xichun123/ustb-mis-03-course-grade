-- =====================================================================
-- 03 组 · 选课及成绩管理 —— 外部引用桩表（可选，v1）
-- 文件：建表SQL_外部引用桩表_可选_朱鳞太_v1.sql
-- 依据：数据字典_03组共享表_朱鳞太_v6.xlsx，工作表“03组_选课及成绩管理”
--       第 131–179 行“本组只读引用的其他组字段”（= 分析报告表 2-16）
-- 目标：MySQL 8.0.16 及以上；状态：只做过静态检查，未在任何 MySQL 实例上执行
--
-- 用途：本组单独开发、联调时，在没有第 1、2、4、13 组正式表的空库里
--       临时代替对方的表，让“组间集成外键”脚本有表可引用。
-- 原则：
-- 1. 只建 v6 里已确认的字段，表名、字段名、类型照对方数据字典，不改。
--    只有 int(2)、smallint(4) 这类显示宽度不写（显示宽度不是取值范围）。
-- 2. 对方没有写非空的列，这里不加 NOT NULL；不加默认值，不加 CHECK。
-- 3. 桩表之间不建外键：teaching_class.course_id → course 等是对方组内部的
--    约束，由对方定义。
-- 4. DBA 合库时直接用对方正式表，不执行本脚本。若库里已有同名正式表，
--    本脚本会因表已存在而报错，这是有意的：不要用桩表覆盖正式表。
--
-- 执行顺序：① 基线脚本（12 表）→ ② 本脚本 → ③ 组间集成外键脚本
-- （① ② 互不依赖，先后可换；③ 必须在 ① ② 之后）
--
-- 本脚本有意不建的外部表（原因详见规范化报告第 7 节）：
--   schedule_result    v6 只列了 week_range、week_day、start_slot、end_slot、
--                      room_id，没有主键，也没有和教学班关联的列；不自造 tc_id
--   t_user             v6 只有 role_code，主键及其类型未给出
--   training_scheme、major、college  v6 只作为被引用方出现，没有给出字段
-- 本脚本有意不建的列：
--   student.major_id char(6)、student.college_id char(4)
--                      v6 第 138、139 行列出，但这是本组向第 1 组提出的新增要求，
--                      “待确认事项”第 6 条仍为“待回复”，第 1 组表里目前没有这两列
--
-- 无主键的两张桩表：scheme_assignment、scheme_course（第 13 组）
--   任务要求文件“建表约定”要求先建这两张引用表，v6 也列出了它们的字段和类型；
--   但主键 assignment_id、scheme_course_id 只在 v6“v4修改说明”第 16、18 行提到名称，
--   类型没有给出。为不自造主键，这两张表只建 v6 已列的字段，不建主键、唯一约束和外键，
--   只供查询联调。注意：
--   - 库参数 sql_require_primary_key 为 ON 时，MySQL 会拒绝这两条语句。共用服务器
--     若开启了该参数，先跳过这两张表，等第 13 组给出主键类型再补；
--   - 合库时直接用第 13 组的正式表，这两张桩表连同其余桩表一起不执行。
--
-- 覆盖范围（不要据此宣称已覆盖全部外部引用）：
--   本脚本够支撑“组间集成外键”脚本里的 15 个外键、一般的选课/成绩联调，
--   以及研究生可选课程范围的查询联调。下列功能不能只靠本桩表离线联调：
--   - 选课时间冲突检测、选课列表上课地点（要 schedule_result，且缺少与教学班的关联列）
--   - 研究生方案版本的专业、年级归属（要 training_scheme，v6 未给字段）
--   - 限选专业/学院、专业排名（要 student.major_id、college_id，第 1 组未确认）
--   - 按角色控制权限（要 t_user 及学生登录表，DBA 未定）
-- =====================================================================

SET NAMES utf8mb4;

-- 第 1 组 学生（v6 第 133–140 行，不含第 138、139 行）
CREATE TABLE student (
  student_id   CHAR(20)    NOT NULL COMMENT '学号',
  student_name VARCHAR(50) NULL COMMENT '姓名',
  grade        SMALLINT    NULL COMMENT '年级（v6 写 smallint(4)，4 为显示宽度）',
  degree_level VARCHAR(20) NULL COMMENT '培养层次，如 本科、研究生',
  class_name   VARCHAR(50) NULL COMMENT '班级',
  final_enroll VARCHAR(20) NULL COMMENT '学籍状态，取值待第1组给出（待确认事项第7条）',
  PRIMARY KEY (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '桩表：第1组 student 中本组用到的已确认字段';

-- 第 2 组 课程（v6 第 141–146 行）
CREATE TABLE course (
  course_id    CHAR(8)      NOT NULL COMMENT '课程号',
  course_name  VARCHAR(40)  NULL COMMENT '课程名称',
  course_type  CHAR(2)      NULL COMMENT '课程性质，11校级必修…16实践',
  credit       DECIMAL(3,1) NULL COMMENT '学分',
  assess_type  CHAR(1)      NULL COMMENT '考核方式：1考试 2考查',
  open_college CHAR(4)      NULL COMMENT '开课学院（关联 college，college 表未建）',
  PRIMARY KEY (course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '桩表：第2组 course 中本组用到的字段';

-- 第 2 组 专业培养计划课程（v6 第 147–149 行，三列联合主键）
CREATE TABLE program_course (
  major_id  CHAR(6) NOT NULL COMMENT '专业编号',
  grade     CHAR(4) NOT NULL COMMENT '适用年级',
  course_id CHAR(8) NOT NULL COMMENT '课程号',
  PRIMARY KEY (major_id, grade, course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '桩表：第2组 program_course 中本组用到的字段';

-- 第 2 组 教学班（v6 第 162–170 行）
-- 注意：teacher_id 是 char(8)，与第 4 组 t_employee.emp_id char(10) 不一致
-- （待确认事项第 11 条，待 DBA 统一）。这里照第 2 组原样写，不擅自改成 char(10)。
CREATE TABLE teaching_class (
  tc_id      VARCHAR(20)  NOT NULL COMMENT '教学班号',
  term       CHAR(11)     NULL COMMENT '学期，如 2026-2027-1',
  course_id  CHAR(8)      NULL COMMENT '课程号（关联 course，对方组内外键，本桩表不建）',
  teacher_id CHAR(8)      NULL COMMENT '任课教师（第2组写 char(8)，与 emp_id char(10) 不一致，待 DBA 统一）',
  class_ids  VARCHAR(100) NULL COMMENT '面向班级',
  capacity   INT          NULL COMMENT '容量上限（v6 写 int(2)，2 为显示宽度）',
  tc_type    CHAR(1)      NULL COMMENT '教学班类型：1必修班 2选修班 3双学位班',
  tc_status  CHAR(1)      NULL COMMENT '教学班状态：只开放 4已发布；2不开课、5取消 不开放',
  actual_num INT          NULL COMMENT '实际选课人数，由第3组正选结束后回写（v6 写 int(2)）',
  PRIMARY KEY (tc_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '桩表：第2组 teaching_class 中本组用到的字段';

-- 第 4 组 教职工（v6 第 176–178 行）
CREATE TABLE t_employee (
  emp_id   CHAR(10)    NOT NULL COMMENT '工号',
  emp_name VARCHAR(30) NULL COMMENT '姓名',
  org_id   CHAR(6)     NULL COMMENT '所属学院（第4组编码 char(6)，与 college_id char(4) 不一致，待 DBA 统一）',
  PRIMARY KEY (emp_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '桩表：第4组 t_employee 中本组用到的字段';

-- 第 13 组 研究方向（v6 第 155–156 行）
CREATE TABLE grad_direction (
  direction_id   VARCHAR(10) NOT NULL COMMENT '研究方向编号',
  direction_name VARCHAR(50) NOT NULL COMMENT '研究方向名称',
  PRIMARY KEY (direction_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '桩表：第13组 grad_direction 中本组用到的字段';

-- 第 13 组 学生适用方案（v6 第 150–154 行）——无主键，见文件头说明
CREATE TABLE scheme_assignment (
  student_id     CHAR(20)    NOT NULL COMMENT '学号，引用 student.student_id（对方组内外键，本桩表不建）',
  scheme_id      VARCHAR(32) NOT NULL COMMENT '方案版本标识，关联 training_scheme.scheme_id（该表未建）',
  direction_id   VARCHAR(10) NULL     COMMENT '研究方向编号，关联 grad_direction.direction_id；为空表示该方案不区分方向',
  effective_from DATE        NOT NULL COMMENT '开始日期',
  effective_to   DATE        NULL     COMMENT '结束日期，为空表示持续适用'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '桩表：第13组 scheme_assignment；主键 assignment_id 类型未给出，无主键，仅查询联调';

-- 第 13 组 方案课程（v6 第 157–161 行）——无主键，见文件头说明
CREATE TABLE scheme_course (
  scheme_id       VARCHAR(32) NOT NULL COMMENT '方案版本标识，关联 training_scheme.scheme_id（该表未建）',
  course_id       CHAR(8)     NOT NULL COMMENT '课程号，关联 course.course_id（对方组内外键，本桩表不建）',
  direction_id    VARCHAR(10) NULL     COMMENT '研究方向编号，为空表示年度通用课程，判断用 IS NULL',
  course_category TINYINT     NOT NULL COMMENT '课程类别：1年度必修 2方向必修 3方向选修（v6 写 tinyint(1)）',
  suggest_term    TINYINT     NULL     COMMENT '建议修读学期（v6 写 tinyint(1)）'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '桩表：第13组 scheme_course；主键 scheme_course_id 类型未给出，无主键，仅查询联调';

-- 结束：8 张桩表（6 张有主键，2 张无主键）。下一步（可选）：建表SQL_组间集成外键_可选_朱鳞太_v1.sql
