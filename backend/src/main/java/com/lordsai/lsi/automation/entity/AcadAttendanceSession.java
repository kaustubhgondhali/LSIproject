package com.lordsai.lsi.automation.entity;

import com.lordsai.lsi.entity.BaseEntity;
import com.lordsai.lsi.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** One class meeting of a batch; attendance records hang off it. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "acad_attendance_sessions")
public class AcadAttendanceSession extends BaseEntity {

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private AcadBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private AcadCourse course;

    @Column(name = "session_type", nullable = false, length = 40)
    private String sessionType = "CLASS";

    @Column(length = 100)
    private String instructor;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;
}
