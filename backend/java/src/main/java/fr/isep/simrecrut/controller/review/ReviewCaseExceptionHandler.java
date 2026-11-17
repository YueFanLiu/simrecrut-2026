package fr.isep.simrecrut.controller.review;

import fr.isep.simrecrut.controller.candidate.CandidateCaseExceptionHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Profile("!resume-workspace")
@RestControllerAdvice(assignableTypes = {HrCaseController.class, ReviewerTaskController.class, ResearchCaseReviewController.class})
public class ReviewCaseExceptionHandler extends CandidateCaseExceptionHandler { }
