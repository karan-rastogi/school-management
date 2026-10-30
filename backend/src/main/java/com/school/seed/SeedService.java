package com.school.seed;

import com.school.academic.domain.AcademicYear;
import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.AcademicYearRepository;
import com.school.academic.repository.SchoolClassRepository;
import com.school.attendance.domain.Attendance;
import com.school.attendance.domain.AttendanceSource;
import com.school.attendance.domain.AttendanceStatus;
import com.school.attendance.repository.AttendanceRepository;
import com.school.calendar.domain.SchoolCalendarDayType;
import com.school.calendar.domain.SchoolCalendarEntry;
import com.school.calendar.repository.SchoolCalendarRepository;
import com.school.student.domain.ParentStudentLink;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.repository.ParentStudentLinkRepository;
import com.school.student.repository.StudentEnrollmentRepository;
import com.school.student.repository.StudentRepository;
import com.school.teacher.domain.TeacherAssignment;
import com.school.teacher.repository.TeacherAssignmentRepository;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.domain.UserRole;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Creates a small, self-contained data set for manually testing the three roles. The password for
 * every seeded account comes from the caller (the {@code SEED_TEST_PASSWORD} environment variable);
 * nothing is hard-coded and every password is stored as a BCrypt hash only.
 */
@Service
public class SeedService {

    private static final int SAMPLE_ATTENDANCE_DAYS = 10;

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final SchoolCalendarRepository schoolCalendarRepository;
    private final AttendanceRepository attendanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public SeedService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            AcademicYearRepository academicYearRepository,
            SchoolClassRepository schoolClassRepository,
            StudentRepository studentRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            ParentStudentLinkRepository parentStudentLinkRepository,
            TeacherAssignmentRepository teacherAssignmentRepository,
            SchoolCalendarRepository schoolCalendarRepository,
            AttendanceRepository attendanceRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.academicYearRepository = academicYearRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.studentRepository = studentRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
        this.schoolCalendarRepository = schoolCalendarRepository;
        this.attendanceRepository = attendanceRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public SeedCounts seedAll(String password) {
        LocalDate today = LocalDate.now(clock);

        AcademicYear year = academicYear(today);
        SchoolClass sixA = schoolClass("6", "A", year);
        SchoolClass sevenA = schoolClass("7", "A", year);

        User admin = user("admin@test.com", "School Admin", password, Role.ADMIN);
        User classTeacher = user("classteacher@test.com", "Class Teacher", password, Role.TEACHER);
        User maths = user("maths@test.com", "Maths Teacher", password, Role.TEACHER);
        User hindi = user("hindi@test.com", "Hindi Teacher", password, Role.TEACHER);
        User parent1 = user("parent1@test.com", "Parent One", password, Role.PARENT);
        User parent2 = user("parent2@test.com", "Parent Two", password, Role.PARENT);
        User parent3 = user("parent3@test.com", "Parent Three", password, Role.PARENT);

        List<Student> sixAStudents = students(year, 1, 4);
        List<Student> sevenAStudents = students(year, 5, 8);
        int enrollments = 0;
        enrollments += enroll(sixAStudents, sixA, year);
        enrollments += enroll(sevenAStudents, sevenA, year);

        int links = 0;
        links += link(parent1, sixAStudents.get(0), "FATHER");
        links += link(parent1, sevenAStudents.get(0), "FATHER");
        links += link(parent2, sixAStudents.get(1), "MOTHER");
        links += link(parent3, sevenAStudents.get(1), "GUARDIAN");

        int assignments = 0;
        assignments += assignment(classTeacher, sixA, "English", true);
        assignments += assignment(maths, sixA, "Maths", false);
        assignments += assignment(maths, sevenA, "Maths", false);
        assignments += assignment(hindi, sixA, "Hindi", false);

        int holidays = holidays(today);

        List<Student> allStudents = new ArrayList<>(sixAStudents);
        allStudents.addAll(sevenAStudents);
        List<LocalDate> attendanceDays = lastWorkingDays(today, SAMPLE_ATTENDANCE_DAYS);
        int marked = 0;
        int unmarked = 0;
        for (int day = 0; day < attendanceDays.size(); day++) {
            for (int student = 0; student < allStudents.size(); student++) {
                if (isUnmarked(day, student)) {
                    unmarked++;
                    continue;
                }
                markAttendance(allStudents.get(student), classTeacher, attendanceDays.get(day), statusFor(day, student));
                marked++;
            }
        }

        return new SeedCounts(
                7, 7, 1, 2, 8, enrollments, links, assignments, holidays, marked, unmarked);
    }

    private AcademicYear academicYear(LocalDate today) {
        int startYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        AcademicYear year = new AcademicYear();
        year.setName(startYear + "-" + (startYear + 1));
        year.setStartDate(LocalDate.of(startYear, 4, 1));
        year.setEndDate(LocalDate.of(startYear + 1, 3, 31));
        year.setCurrent(true);
        return academicYearRepository.saveAndFlush(year);
    }

    private SchoolClass schoolClass(String name, String section, AcademicYear year) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setName(name);
        schoolClass.setSection(section);
        schoolClass.setAcademicYear(year);
        return schoolClassRepository.saveAndFlush(schoolClass);
    }

    private User user(String email, String name, String password, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(false);
        User saved = userRepository.saveAndFlush(user);

        UserRole userRole = new UserRole();
        userRole.setUserId(saved.getId());
        userRole.setRole(role);
        userRoleRepository.saveAndFlush(userRole);
        return saved;
    }

    private List<Student> students(AcademicYear year, int first, int last) {
        List<Student> students = new ArrayList<>();
        for (int number = first; number <= last; number++) {
            Student student = new Student();
            student.setFullName("Student " + number);
            student.setAdmissionNo(String.format("STU-%04d", number));
            student.setDob(year.getStartDate().minusYears(11));
            student.setAdmissionDate(year.getStartDate());
            students.add(studentRepository.saveAndFlush(student));
        }
        return students;
    }

    private int enroll(List<Student> students, SchoolClass schoolClass, AcademicYear year) {
        for (int index = 0; index < students.size(); index++) {
            StudentEnrollment enrollment = new StudentEnrollment();
            enrollment.setStudent(students.get(index));
            enrollment.setSchoolClass(schoolClass);
            enrollment.setRollNo(index + 1);
            enrollment.setFromDate(year.getStartDate());
            studentEnrollmentRepository.saveAndFlush(enrollment);
        }
        return students.size();
    }

    private int link(User parent, Student student, String relationship) {
        ParentStudentLink link = new ParentStudentLink();
        link.setParentUserId(parent.getId());
        link.setStudentId(student.getId());
        link.setRelationship(relationship);
        parentStudentLinkRepository.saveAndFlush(link);
        return 1;
    }

    private int assignment(User teacher, SchoolClass schoolClass, String subject, boolean classTeacher) {
        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setTeacher(teacher);
        assignment.setSchoolClass(schoolClass);
        assignment.setSubjectName(subject);
        assignment.setClassTeacher(classTeacher);
        assignment.setActive(true);
        teacherAssignmentRepository.saveAndFlush(assignment);
        return 1;
    }

    private int holidays(LocalDate today) {
        saveHoliday(today.minusDays(30), "Founders' Day");
        saveHoliday(today.minusDays(20), "Staff Development Day");
        saveHoliday(today.plusDays(15), "Mid-term Break");
        return 3;
    }

    private void saveHoliday(LocalDate date, String description) {
        SchoolCalendarEntry entry = new SchoolCalendarEntry();
        entry.setDate(date);
        entry.setType(SchoolCalendarDayType.HOLIDAY);
        entry.setDescription(description);
        schoolCalendarRepository.saveAndFlush(entry);
    }

    private List<LocalDate> lastWorkingDays(LocalDate today, int count) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = today.minusDays(1);
        while (days.size() < count) {
            DayOfWeek dayOfWeek = cursor.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                days.add(cursor);
            }
            cursor = cursor.minusDays(1);
        }
        Collections.reverse(days);
        return days;
    }

    private static boolean isUnmarked(int day, int student) {
        return day == SAMPLE_ATTENDANCE_DAYS - 1 && student % 3 == 0;
    }

    private static AttendanceStatus statusFor(int day, int student) {
        int bucket = Math.floorMod(day * 3 + student, 7);
        return switch (bucket) {
            case 4 -> AttendanceStatus.ABSENT;
            case 5 -> AttendanceStatus.LATE;
            case 6 -> AttendanceStatus.HALF_DAY;
            default -> AttendanceStatus.PRESENT;
        };
    }

    private void markAttendance(Student student, User markedBy, LocalDate date, AttendanceStatus status) {
        Attendance attendance = new Attendance();
        attendance.setStudent(student);
        attendance.setMarkedBy(markedBy);
        attendance.setDate(date);
        attendance.setStatus(status);
        attendance.setSource(AttendanceSource.MANUAL);
        attendanceRepository.saveAndFlush(attendance);
    }
}
