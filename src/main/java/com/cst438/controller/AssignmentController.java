package com.cst438.controller;

import com.cst438.domain.*;
import com.cst438.dto.AssignmentDTO;
import com.cst438.dto.AssignmentStudentDTO;
import com.cst438.dto.GradeDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;


import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class AssignmentController {

    @Autowired
    AssignmentRepository assignmentRepository;

    @Autowired
    SectionRepository sectionRepository;

    @Autowired
    GradeRepository gradeRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Autowired
    UserRepository userRepository;


    // instructor lists assignments for a section.  Assignments ordered by due date.
    // logged in user must be the instructor for the section
    @GetMapping("/sections/{secNo}/assignments")
    public List<AssignmentDTO> getAssignments(@PathVariable("secNo") int secNo) {

        Section section = sectionRepository.findById(secNo).orElse(null);
        if (section == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found");
        }

        User instructor = userRepository.findByEmail(section.getInstructorEmail());
        String instructorName = instructor.getName();

        if (!instructor.getType().equals("INSTRUCTOR") || !section.getInstructorEmail().equals(instructor.getEmail())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Only INSTRUCTOR can view assignments for this section");
        }

        List<Assignment> assignments = assignmentRepository.findBySectionNoOrderByDueDate(secNo);
        List<AssignmentDTO> assignmentDTOs = new ArrayList<>();
        for (Assignment a : assignments) {
            assignmentDTOs.add(new AssignmentDTO(
                    a.getAssignmentId(),
                    a.getTitle(),
                    a.getDueDate(),
                    a.getCourseId(),
                    a.getSection().getSecId(),
                    a.getSection().getSectionNo()));
        }
        return assignmentDTOs;
    }


    // add assignment
    // user must be instructor of the section
    // return AssignmentDTO with assignmentID generated by database
    @PostMapping("/assignments")
    public AssignmentDTO createAssignment(@RequestBody AssignmentDTO dto) {

        Section section = sectionRepository.findById(dto.secNo()).orElse(null);

        if (section == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found");
        }

        User instructor = userRepository.findByEmail(section.getInstructorEmail());

        // Check if the retrieved instructor matches the one associated with the section
        if (!instructor.getEmail().equals(section.getInstructorEmail())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Only Instructor can add assignments for this section");
        }

        if (dto.dueDate().before(section.getTerm().getStartDate()) || dto.dueDate().after(section.getTerm().getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment due date is outside of the course term");
        }

        Assignment a = new Assignment();
        a.setTitle(dto.title());
        a.setDueDate(dto.dueDate());
        a.setSection(section);
        assignmentRepository.save(a);

        return new AssignmentDTO(
                a.getAssignmentId(),
                a.getTitle(),
                a.getDueDate(),
                a.getCourseId(),
                a.getSection().getSecId(),
                a.getSection().getSectionNo());
    }


    // update assignment for a section.  Only title and dueDate may be changed.
    // user must be instructor of the section
    // return updated AssignmentDTO
    @PutMapping("/assignments")
    public AssignmentDTO updateAssignment(@RequestBody AssignmentDTO dto) {

        Assignment a = assignmentRepository.findById(dto.id()).orElse(null);

        if (a == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found");
        }

        Section section = a.getSection();

        User instructor = userRepository.findByEmail(section.getInstructorEmail());

        // Check if the user is authorized to update the assignment
        if (!instructor.getEmail().equals(section.getInstructorEmail())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Only INSTRUCTOR can update assignments for this section");
        }

            if (dto.dueDate().before(section.getTerm().getStartDate()) || dto.dueDate().after(section.getTerm().getEndDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment due date is outside of the course term");
            }

            a.setTitle(dto.title());
            a.setDueDate(dto.dueDate());
            assignmentRepository.save(a);

        return new AssignmentDTO(
                a.getAssignmentId(),
                a.getTitle(),
                a.getDueDate(),
                a.getCourseId(),
                a.getSection().getSecId(),
                a.getSection().getSectionNo());
    }


    // delete assignment for a section
    // logged in user must be instructor of the section
    @DeleteMapping("/assignments/{assignmentId}")
    public void deleteAssignment(@PathVariable("assignmentId") int assignmentId) {

        Assignment a = assignmentRepository.findById(assignmentId).orElse(null);
        if (a == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found");
        }

        Section section = a.getSection();

        User instructor = userRepository.findByEmail(section.getInstructorEmail());

        // Check if the user is authorized to delete the assignment
        if (!instructor.getEmail().equals(section.getInstructorEmail())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Only INSTRUCTOR can delete assignments for this section");
        }

        assignmentRepository.delete(a);
    }


    // instructor gets grades for assignment ordered by student name
    // user must be instructor for the section
    @GetMapping("/assignments/{assignmentId}/grades")
    public List<GradeDTO> getAssignmentGrades(@PathVariable("assignmentId") int assignmentId) {

        Assignment a = assignmentRepository.findById(assignmentId).orElse(null);
        if (a == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found");
        }
        Section section = a.getSection();

        User instructor = userRepository.findByEmail(section.getInstructorEmail());

        // Check if the user is authorized to view grades for the assignment
        if (!instructor.getEmail().equals(section.getInstructorEmail())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Only INSTRUCTOR can view grades for this assignment");
        }

        List<GradeDTO> gradeDTOs = new ArrayList<>();
        List<Enrollment> enrollments = enrollmentRepository.findEnrollmentsBySectionNoOrderByStudentName(a.getSection().getSectionNo());
        
        for (Enrollment enrollment : enrollments) {
            Grade grade = gradeRepository.findByEnrollmentIdAndAssignmentId(enrollment.getEnrollmentId(), a.getAssignmentId());
            if (grade == null) {
                grade = new Grade();
                grade.setEnrollment(enrollment);
                grade.setAssignment(a);
                grade.setScore(null);
                gradeRepository.save(grade); // Save the new grade
            }

            gradeDTOs.add(new GradeDTO(
                    grade.getGradeId(),
                    grade.getEnrollment().getStudent().getName(),
                    grade.getEnrollment().getStudent().getEmail(),
                    grade.getAssignment().getTitle(),
                    grade.getEnrollment().getSection().getCourse().getCourseId(),
                    grade.getEnrollment().getSection().getSecId(),
                    grade.getScore()
            ));
        }
        return gradeDTOs;
    }


    // instructor uploads grades for assignment
    // user must be instructor for the section
    @PutMapping("/grades")
    public void updateGrades(@RequestBody List<GradeDTO> dlist) {

        for (GradeDTO dto : dlist) {
            Grade grade = gradeRepository.findById(dto.gradeId()).orElse(null);
            Section section = sectionRepository.findById(dto.sectionId()).orElse(null);

            if (grade != null) {
                grade.setScore(dto.score());
                gradeRepository.save(grade);
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "grade not found " + dto.gradeId());
            }
        }
    }


    // student lists their assignments/grades for an enrollment ordered by due date
    // student must be enrolled in the section
    @GetMapping("/assignments")
    public List<AssignmentStudentDTO> getStudentAssignments(
        @RequestParam("studentId") int studentId,
        @RequestParam("year") int year,
        @RequestParam("semester") String semester) {

        User student = userRepository.findById(studentId).orElse(null);
        if (student == null || !student.getType().equals("STUDENT")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found or not a student");
        }

        List<AssignmentStudentDTO> assignmentStudentDTOs = new ArrayList<>();
        List<Assignment> assignments = assignmentRepository.findByStudentIdAndYearAndSemesterOrderByDueDate(studentId, year, semester);
        for (Assignment assignment : assignments) {
            Enrollment enrollment = enrollmentRepository.findEnrollmentBySectionNoAndStudentId(student.getId(), assignment.getSection().getSecId());
            Grade grade = gradeRepository.findByEnrollmentIdAndAssignmentId(enrollment.getEnrollmentId(), assignment.getAssignmentId());
            if (grade == null) {
                grade = new Grade();
                grade.setEnrollment(enrollment);
                grade.setAssignment(assignment);
                grade.setScore(null); // Assuming score is nullable
                gradeRepository.save(grade); // Save the new grade
            }

            assignmentStudentDTOs.add(new AssignmentStudentDTO(
                    assignment.getAssignmentId(),
                    assignment.getTitle(),
                    assignment.getDueDate(),
                    assignment.getCourseId(),
                    assignment.getSection().getSecId(),
                    grade.getScore()
            ));
        }
        return assignmentStudentDTOs;
    }
}

