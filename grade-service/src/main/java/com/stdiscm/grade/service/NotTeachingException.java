package com.stdiscm.grade.service;

/** Faculty tried to upload grades for a course they do not teach. Sent as PERMISSION_DENIED. */
public class NotTeachingException extends RuntimeException {

    public NotTeachingException(String courseCode) {
        super("you do not teach " + courseCode);
    }
}
