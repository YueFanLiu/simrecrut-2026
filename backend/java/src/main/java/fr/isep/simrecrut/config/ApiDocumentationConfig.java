package fr.isep.simrecrut.config;

import static fr.isep.simrecrut.config.ApiDocumentationModels.*;

import fr.isep.simrecrut.dto.identity.AuthenticationDtos.*;
import fr.isep.simrecrut.dto.identity.AccountManagementDtos.*;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.*;
import fr.isep.simrecrut.dto.identity.RoleDtos.RoleSummary;
import fr.isep.simrecrut.dto.job.JobDtos.Validation;
import fr.isep.simrecrut.dto.assessment.CandidateCaseDtos;
import io.swagger.v3.core.converter.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import java.util.*;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@Profile("!resume-workspace")
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class ApiDocumentationConfig {
    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI simrecrutOpenApi() {
        return new OpenAPI().openapi("3.0.3")
                .info(new Info().title("SimRecrut Backend API").version("0.1.0")
                        .description("Implemented backend routes only. Live database/provider integration is not certified "
                                + "by this document. Job deployment requires V6 and a frozen matching rule. "
                                + "Independent account management requires V7. "
                                + "Candidate case workflows require V8 and compatible frozen protocols/models. "
                                + "Start with registration/login, then authorize with data.accessToken. "
                                + "Business API errors use code/message/requestId/data. Success code is zero."))
                .servers(List.of(new Server().url("/").description("The same backend that serves this documentation")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    public GlobalOperationCustomizer documentOperations() {
        return (operation, method) -> {
            String controller = method.getBeanType().getSimpleName();
            String name = method.getMethod().getName();
            String tag = switch (controller) {
                case "AuthenticationController" -> "Authentication";
                case "AdminRoleController", "AdminUserController", "RolePermissionController" -> "Administration";
                case "PasswordResetController" -> "Authentication";
                case "ResumeController" -> "Candidate resumes";
                case "CandidateJobController" -> "Candidate jobs";
                case "CandidateCaseController" -> "Candidate cases";
                case "HrJobController" -> "HR jobs";
                default -> controller.replace("Controller", "");
            };
            operation.setTags(List.of(tag));
            operation.addExtension("x-implementation-status", "implemented; live integration verification may be pending");
            operation.addExtension("x-java-handler", controller + "." + name);
            boolean publicAuth = controller.equals("PasswordResetController")
                    || controller.equals("AuthenticationController")
                    && Set.of("requestCode", "register", "login").contains(name);
            operation.setSecurity(publicAuth ? List.of() : List.of(new SecurityRequirement().addList(BEARER)));
            if (operation.getSummary() == null || operation.getSummary().isBlank()) {
                operation.setSummary(summary(controller, name));
            }
            if (operation.getDescription() == null || operation.getDescription().isBlank()) {
                operation.setDescription(description(controller, name));
            }
            if (operation.getParameters() != null) {
                for (Parameter parameter : operation.getParameters()) {
                    if ("Idempotency-Key".equals(parameter.getName())) {
                        parameter.setDescription("Required for writes. Reuse the same key for an unchanged retry; "
                                + "use a new key for changed input or an intentional new action.");
                        parameter.setSchema(new StringSchema().pattern("^[A-Za-z0-9_-]{16,128}$")
                                .minLength(16).maxLength(128));
                        parameter.setExample("550e8400-e29b-41d4-a716-446655440000");
                    }
                    if ("path".equals(parameter.getIn()) && !"roleCode".equals(parameter.getName())) {
                        parameter.setSchema(new StringSchema().pattern(controller.equals("ResumeController")
                                ? "^[a-f0-9]{32}$" : "^[0-9A-HJKMNP-TV-Z]{26}$"));
                    }
                    if ("page".equals(parameter.getName())) parameter.setSchema(new IntegerSchema()
                            .minimum(java.math.BigDecimal.ONE).maximum(java.math.BigDecimal.valueOf(100000))._default(1));
                    if ("pageSize".equals(parameter.getName())) parameter.setSchema(new IntegerSchema()
                            .minimum(java.math.BigDecimal.ONE).maximum(java.math.BigDecimal.valueOf(100))._default(20));
                    if ("keyword".equals(parameter.getName())) parameter.setSchema(new StringSchema().maxLength(100)._default(""));
                }
            }
            return operation;
        };
    }

    @Bean
    public GlobalOpenApiCustomizer documentResponses() {
        return api -> {
            if (api.getComponents() == null) api.setComponents(new Components());
            if (api.getPaths() == null) return;
            api.getComponents().addSchemas("ApiErrorEnvelope", envelope(model(api, ErrorData.class), true));
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((verb, operation) -> {
                Object handler = operation.getExtensions() == null ? null : operation.getExtensions().get("x-java-handler");
                if (!(handler instanceof String key)) return;
                String controller = key.substring(0, key.indexOf('.'));
                String method = key.substring(key.indexOf('.') + 1);
                Schema<?> data = responseSchema(api, controller, method, verb);
                if (data == null) return;
                ApiResponses responses = new ApiResponses();
                int success = successStatus(controller, method);
                responses.addApiResponse(Integer.toString(success), response("Successful request", envelope(data, false)));
                if (controller.equals("ResumeController") && method.equals("importFile")) {
                    responses.addApiResponse("200", response("The retained PDF already exists", envelope(data, false)));
                }
                if (controller.equals("ResumeController") && method.equals("delete")) {
                    responses.addApiResponse("202", response("Access revoked; physical cleanup is still pending", envelope(data, false)));
                }
                if (controller.equals("CandidateCaseController") && method.equals("delete")) {
                    responses.addApiResponse("200", response("Deletion completed on a repeated request", envelope(data, false)));
                }
                for (int status : new int[]{400, 401, 403, 404, 409, 422, 429, 500, 503}) {
                    if (status == 429 && !Set.of("AuthenticationController", "PasswordResetController").contains(controller)) continue;
                    var error = response(errorDescription(status), new Schema<>().$ref("#/components/schemas/ApiErrorEnvelope"));
                    if (status == 429) error.addHeaderObject("Retry-After", new io.swagger.v3.oas.models.headers.Header()
                            .description("Seconds before retrying").schema(new IntegerSchema()));
                    responses.addApiResponse(Integer.toString(status), error);
                }
                operation.setResponses(responses);
            }));
            supplementDynamicFields(api);
        };
    }

    private Schema<?> responseSchema(OpenAPI api, String controller, String method, PathItem.HttpMethod verb) {
        return switch (controller) {
            case "CandidateCaseController" -> model(api, switch (method) {
                case "list" -> CandidateCaseDtos.CasePage.class;
                case "status" -> CandidateCaseDtos.CaseStatus.class;
                case "draft" -> CandidateCaseDtos.ReviewDraft.class;
                case "attributes" -> CandidateCaseDtos.ResearchAttributes.class;
                case "results" -> CandidateCaseDtos.FiveResults.class;
                case "delete" -> CandidateCaseDtos.DeleteResult.class;
                default -> CandidateCaseDtos.CaseAction.class;
            });
            case "AdminUserController" -> model(api, method.equals("list") ? UserPage.class : UserView.class);
            case "RolePermissionController" -> method.equals("permissions")
                    ? new ArraySchema().items(model(api, PermissionView.class)) : model(api, RolePermissionsView.class);
            case "PasswordResetController" -> model(api, switch (method) {
                case "options" -> ResetOptions.class;
                case "requestCode" -> ResetCodeResponse.class;
                default -> ResetResult.class;
            });
            case "AuthenticationController" -> model(api, switch (method) {
                case "requestCode" -> CodeResponse.class;
                case "register" -> RegisterResponse.class;
                case "login" -> LoginResponse.class;
                case "currentUser" -> CurrentUserResponse.class;
                case "logout" -> LogoutResponse.class;
                default -> Object.class;
            });
            case "AdminRoleController" -> new ArraySchema().items(model(api, RoleSummary.class));
            case "CandidateJobController" -> model(api, method.equals("list") ? PublicJobPage.class : PublicJobDetail.class);
            case "HrJobController" -> switch (method) {
                case "list" -> model(api, ManagedJobPage.class);
                case "versions" -> new ArraySchema().items(model(api, ManagedJobSummary.class));
                case "extract", "extraction" -> model(api, ExtractionStatus.class);
                case "validate" -> model(api, Validation.class);
                case "requirements" -> model(api, verb == PathItem.HttpMethod.GET ? RequirementsView.class : ManagedJobDetail.class);
                case "weights" -> model(api, verb == PathItem.HttpMethod.GET ? WeightsView.class : ManagedJobDetail.class);
                case "draft", "update", "create", "cloneDraft", "publish", "version" -> model(api, ManagedJobDetail.class);
                default -> null;
            };
            case "ResumeController" -> switch (method) {
                case "list" -> new ArraySchema().items(model(api, ResumeSummary.class));
                case "detail" -> model(api, ResumeDetail.class);
                case "importFile" -> model(api, ImportResult.class);
                case "clean" -> model(api, ProcessingResult.class);
                case "save", "confirm" -> model(api, SaveResult.class);
                case "publish" -> model(api, PublicationResult.class);
                case "cleanup" -> new BooleanSchema();
                case "delete" -> model(api, DeletionResult.class);
                default -> null;
            };
            default -> null;
        };
    }

    private Schema<?> model(OpenAPI api, Class<?> type) {
        ResolvedSchema resolved = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
        resolved.referencedSchemas.forEach(api.getComponents()::addSchemas);
        return resolved.schema;
    }

    private Schema<?> envelope(Schema<?> data, boolean error) {
        return new ObjectSchema().addProperty("code", error ? new IntegerSchema() : new IntegerSchema()._enum(List.of(0)))
                .addProperty("message", new StringSchema()).addProperty("requestId", new StringSchema())
                .addProperty("data", data).required(List.of("code", "message", "requestId", "data"));
    }

    private io.swagger.v3.oas.models.responses.ApiResponse response(String description, Schema<?> schema) {
        return new io.swagger.v3.oas.models.responses.ApiResponse().description(description)
                .addHeaderObject("Cache-Control", new io.swagger.v3.oas.models.headers.Header()
                        .schema(new StringSchema()._enum(List.of("no-store"))))
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    private void supplementDynamicFields(OpenAPI api) {
        List<Schema> payloads = List.of(model(api, SkillsPayload.class), model(api, ExperiencePayload.class),
                model(api, EducationPayload.class), model(api, LanguagesPayload.class), model(api, ProjectsPayload.class));
        Schema<?> resumeDraft = model(api, ResumeDraft.class);
        Schema<?> caseObjective = model(api, CandidateCaseDtos.ObjectiveView.class);
        Schema<?> caseMl = model(api, CandidateCaseDtos.MlView.class);
        Set<String> strict = Set.of("CreateRequest", "UpdateRequest", "RequirementsRequest", "WeightsRequest",
                "RevisionRequest", "CloneRequest", "EmptyRequest", "ImportRequest", "EditRequest", "Requirement", "Weights",
                "CreateUserRequest", "UpdateUserRequest", "StatusRequest", "RolesRequest", "PermissionsRequest",
                "ResetCodeRequest", "ResetRequest", "CreateCaseRequest", "UpdateDraftRequest",
                "AttributesRequest", "AttributeInput", "ConfirmRequest");
        for (var entry : api.getComponents().getSchemas().entrySet()) {
            Schema<?> schema = entry.getValue();
            if (strict.contains(entry.getKey()) || entry.getKey().startsWith("Job")
                    || entry.getKey().startsWith("Resume")) {
                if ("object".equals(schema.getType())) schema.setAdditionalProperties(false);
            }
            if (schema.getProperties() == null) continue;
            if (Set.of("UpdateDraftRequest", "ReviewDraft").contains(entry.getKey())) {
                schema.getProperties().put("professional", model(api, ProfessionalFacts.class));
            }
            if (entry.getKey().equals("FiveResults")) {
                schema.getProperties().put("objective", new ComposedSchema().allOf(List.of(caseObjective)).nullable(true));
                schema.getProperties().put("neutralMl", new ComposedSchema().allOf(List.of(caseMl)).nullable(true));
                schema.getProperties().put("biasedMl", new ComposedSchema().allOf(List.of(caseMl)).nullable(true));
            }
            Schema<?> payload = (Schema<?>) schema.getProperties().get("payload");
            if (payload != null && schema.getProperties().containsKey("criterionType")) {
                schema.getProperties().put("payload", new ComposedSchema().oneOf(payloads)
                        .description("Use the payload matching criterionType. Drafts may be incomplete; publication validates frozen codes."));
            }
            Schema<?> draft = (Schema<?>) schema.getProperties().get("draft");
            if (draft != null && schema.getProperties().containsKey("expectedRevision")) {
                schema.getProperties().put("draft", resumeDraft);
            }
            if (schema.getProperties().containsKey("password")) {
                for (String name : List.of("password", "passwordConfirmation")) {
                    Schema<?> password = (Schema<?>) schema.getProperties().get(name);
                    if (password != null) { password.setFormat("password"); password.setWriteOnly(true); }
                }
            }
            if (schema.getProperties().containsKey("challengeId") && schema.getProperties().containsKey("accountType")) {
                schema.getProperties().put("accountType", new StringSchema()._enum(List.of("CANDIDATE", "HR"))
                        .description("Public registration never grants ADMIN or RESEARCHER."));
            }
        }
    }

    private int successStatus(String controller, String method) {
        if (controller.equals("CandidateCaseController")) return switch (method) {
            case "create" -> 201;
            case "upload", "run", "retry", "delete" -> 202;
            default -> 200;
        };
        if (controller.equals("AdminUserController") && method.equals("create")) return 201;
        if (controller.equals("AuthenticationController") && method.equals("register")) return 201;
        if (controller.equals("HrJobController") && Set.of("create", "cloneDraft").contains(method)) return 201;
        if (controller.equals("HrJobController") && method.equals("extract")
                || controller.equals("ResumeController") && method.equals("clean")) return 202;
        if (controller.equals("ResumeController") && method.equals("importFile")) return 201;
        return 200;
    }

    private String summary(String controller, String method) {
        if (controller.equals("CandidateCaseController")) return switch (method) {
            case "create" -> "Create a job-linked simulation case";
            case "list" -> "List own retained cases";
            case "upload" -> "Upload a PDF and queue extraction";
            case "status" -> "Read case processing and cleanup state";
            case "draft" -> "Read professional facts for review";
            case "saveDraft" -> "Save professional corrections";
            case "attributes" -> "Read optional research attributes";
            case "saveAttributes" -> "Save optional research attributes";
            case "confirm" -> "Freeze facts and queue original cleanup";
            case "run" -> "Freeze model versions and queue assessment";
            case "retry" -> "Resume a stored retryable failure";
            case "results" -> "Read five permitted result slots";
            default -> "Revoke access and queue complete case deletion";
        };
        if (controller.equals("AdminUserController")) return switch (method) {
            case "list" -> "List safe account summaries";
            case "detail" -> "Read account details";
            case "create" -> "Create an account requiring mailbox password setup";
            case "update" -> "Change account email and require reverification";
            case "status" -> "Enable or disable an account";
            default -> "Replace account roles";
        };
        if (controller.equals("RolePermissionController")) return switch (method) {
            case "permissions" -> "Read the server-owned permission catalogue";
            case "rolePermissions" -> "Read role permissions and revision";
            default -> "Replace role permissions";
        };
        if (controller.equals("PasswordResetController")) return switch (method) {
            case "options" -> "Read password recovery availability";
            case "requestCode" -> "Request mailbox password setup or recovery";
            default -> "Set a password using mailbox proof";
        };
        return switch (method) {
            case "requestCode" -> "Send a registration email code";
            case "register" -> "Register a verified account";
            case "login" -> "Sign in with username or email";
            case "currentUser" -> "Read the current account, roles and permissions";
            case "logout" -> "Revoke the current session";
            case "importFile" -> "Import a temporary PDF resume";
            case "clean" -> "Start professional fact extraction";
            case "confirm" -> "Confirm and lock reviewed professional facts";
            case "cleanup" -> "Retry temporary original cleanup";
            case "delete" -> "Delete an owned resume";
            case "extract" -> "Start job requirement extraction";
            case "extraction" -> "Read job extraction progress";
            case "validate" -> "Validate the saved job draft";
            case "cloneDraft" -> "Create a draft from a published job version";
            case "versions" -> "List managed job versions";
            case "version" -> "Read an immutable version or saved draft";
            case "requirements" -> "Read or replace structured job requirements";
            case "weights" -> "Read or update fractional job weights";
            case "draft" -> "Reload the editable job draft";
            case "update" -> "Update the draft title and description";
            case "create" -> "Create a job and initial draft";
            case "publish" -> controller.equals("ResumeController") ? "Publish confirmed professional JSON" : "Publish an immutable job version";
            case "save" -> "Save the editable professional draft";
            case "list" -> switch (controller) {
                case "AdminRoleController" -> "List available roles";
                case "ResumeController" -> "List own retained resumes";
                case "CandidateJobController" -> "List current published jobs";
                default -> "List managed jobs";
            };
            case "detail" -> controller.equals("ResumeController")
                    ? "Read own professional facts and processing status" : "Read the current published job";
            default -> method;
        };
    }

    private String description(String controller, String method) {
        if (controller.equals("CandidateCaseController")) return "Requires CANDIDATE and the operation-specific "
                + "simrecrut:case permission. Own retained cases only. All writes require Idempotency-Key, "
                + "including creation; draft/attribute/confirmation writes also require revision. Upload uses multipart file. "
                + "Confirmation freezes inputs; run requires compatible models and configured inference paths. "
                + "HTTP 202 is accepted, not completed. Retry preserves successful results and frozen versions. "
                + "PURGING content is inaccessible. Objective and ML scores use separate scales. No sample results are generated.";
        if (controller.equals("AdminUserController")) return "Requires ADMIN and the operation-specific simrecrut:user permission. "
                + "Use public account IDs and the latest revision. Username is immutable; email changes require mailbox proof. "
                + "Created accounts cannot sign in until password setup. Creation does not send email automatically: request a "
                + "password-reset code. Status, role and email changes invalidate old sessions. Preserve the last available Admin.";
        if (controller.equals("RolePermissionController")) return "Requires ADMIN and simrecrut:permission:list, "
                + "simrecrut:role:list or simrecrut:role:edit as applicable. Fixed roles only. Permission writes require revision. "
                + "Reject unknown/out-of-scope permissions and removal of core Admin permissions. Changed grants invalidate member sessions.";
        if (controller.equals("PasswordResetController")) return "Public mailbox proof flow; no RuoYi endpoint or CAPTCHA. "
                + "Codes expire after 600 seconds, are single-use, allow five failures, and are bound to email and challenge. "
                + "Unknown/disabled accounts receive the same code-request response. Sending and verification are rate limited. "
                + "Passwords must contain 15-128 characters. Successful reset invalidates all old sessions. "
                + "A changed account security version invalidates outstanding proofs. No password or code is returned.";
        if (controller.equals("AuthenticationController")) {
            return switch (method) {
                case "requestCode" -> "Public endpoint. Returns a challengeId; the code is delivered by email. Rate limits apply.";
                case "register" -> "Public endpoint. Supply the email challenge/code, terms agreement and CANDIDATE or HR account type.";
                case "login" -> "Public endpoint. Supply identifier and password. Copy data.accessToken into Swagger UI Authorize; enter the token without a Bearer prefix.";
                case "currentUser" -> "Bearer token required. Returns the current database-backed roles and permissions.";
                default -> "Bearer token required. Revokes this Redis session; the token cannot be reused afterward.";
            };
        }
        if (controller.equals("AdminRoleController")) return "Requires ADMIN and simrecrut:role:list. This is role reading only, not user management.";
        if (controller.equals("CandidateJobController")) return "Requires CANDIDATE and simrecrut:job:browse. Only current published jobs and public requirements are returned; drafts and weights are absent.";
        if (controller.equals("ResumeController")) {
            String permission = switch (method) {
                case "list", "detail" -> "list";
                case "importFile" -> "upload";
                case "clean", "save" -> "edit";
                case "confirm" -> "edit and simrecrut:resume:confirm";
                case "delete" -> "delete";
                default -> "confirm";
            };
            return "Requires CANDIDATE and simrecrut:resume:" + permission + ". Own retained resumes only. "
                    + "This library is separate from job assessment cases. Save uses confirmed=false; confirm uses true. "
                    + "Writes require Idempotency-Key. Reload detail for the next expectedRevision. "
                    + "Import accepts Base64 PDF content, not multipart upload. Confirming does not publish automatically.";
        }
        String permission = switch (method) {
            case "list", "draft", "versions", "version", "extraction" -> "list";
            case "create", "cloneDraft" -> "create";
            case "publish", "validate" -> "publish";
            default -> "edit (GET requirements/weights uses list)";
        };
        return "Requires HR and simrecrut:job:" + permission + ". Managed jobs only. "
                + "Use the revision returned by each preceding write. Writes require Idempotency-Key; validation is read-only. "
                + "Weights are fractions totalling exactly one. Published versions cannot be edited. "
                + "HTTP 202 means queued, not complete: poll extraction-status, then reload the draft. "
                + "V6 and a usable frozen rule are deployment prerequisites; extraction additionally needs the configured provider.";
    }

    private String errorDescription(int status) {
        return switch (status) {
            case 400 -> "Invalid fields, PDF, operation key or revision";
            case 401 -> "Missing/invalid authentication or invalid login credentials";
            case 403 -> "Required role or action permission is missing";
            case 404 -> "Unknown, foreign or expired resource";
            case 409 -> "Revision, workflow state, existing draft or operation-key conflict";
            case 429 -> "Rate limited; respect Retry-After";
            case 503 -> "Storage, rule configuration or external dependency is unavailable";
            default -> "Unexpected failure with a safe message";
        };
    }
}
