package com.sterul.opencookbookapiserver.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import java.util.Collection;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.households.ConditionalOnHouseholdsEnabled;
import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.support.PlanScopes;
import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/** Keeps the access checks and layer boundaries unavoidable rather than customary. */
@AnalyzeClasses(
        packages = "com.sterul.opencookbookapiserver",
        importOptions = ImportOption.DoNotIncludeTests.class)
class AccessRuleArchitectureTest {

    private static final String WEB_LAYER = "com.sterul.opencookbookapiserver.controllers";

    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Controllers").definedBy("..controllers..")
            .layer("Services").definedBy("..services..")
            .layer("Repositories").definedBy("..repositories..")
            .whereLayer("Controllers").mayNotBeAccessedByAnyLayer()
            .whereLayer("Services").mayOnlyBeAccessedByLayers("Controllers")
            .whereLayer("Repositories").mayOnlyBeAccessedByLayers("Services")
            // Resolves the signed in user, before anything can be authorised.
            .ignoreDependency(BaseController.class, UserRepository.class)
            .because("the access rule lives in the services, and reaching past it is how it gets skipped");

    @ArchTest
    static final ArchRule theUncheckedFinderHasOnlyItsTwoCallers = noClasses()
            .that().resideOutsideOfPackages("..controllers.admin..", "..services.sharing..")
            .and().doNotBelongToAnyOf(RecipeService.class)
            .should().callMethod(RecipeService.class, "getRecipeIgnoringAccess", Long.class)
            .because("every other caller has a viewer and must say so, via getRecipeFor or getOwnRecipe");

    @ArchTest
    static final ArchRule recipesAreLoadedByIdOnlyInRecipeService = noClasses()
            .that().doNotBelongToAnyOf(RecipeService.class)
            .should().callMethodWhere(DescribedPredicate.describe("a RecipeRepository lookup by id",
                    (JavaMethodCall call) -> call.getTargetOwner().isAssignableTo(RecipeRepository.class)
                            && call.getName().matches("findById|findAllById|getReferenceById|getById")))
            .because("RecipeService is where a recipe id meets the access rule");

    @ArchTest
    static final ArchRule householdsDoNotReachIntoRecipesOrPlanning = noClasses()
            .that().resideInAPackage("..services.households..")
            .should().dependOnClassesThat().haveSimpleName("RecipeService")
            .orShould().dependOnClassesThat().haveSimpleName("WeekplanService")
            .orShould().dependOnClassesThat().resideInAPackage("..services.planning..")
            .because("a membership change is announced, not applied - see ReadAccessNarrowed");

    @ArchTest
    static final ArchRule membershipsAreReadOnlyByTheHouseholdPackage = noClasses()
            .that().resideOutsideOfPackages("..services.households..", "..configurations.households..",
                    "..repositories..")
            .should().dependOnClassesThat().haveSimpleName("HouseholdMembershipRepository")
            .because("who may read whose cookbook is answered in one place");

    @ArchTest
    static final ArchRule onlyPlanScopesTurnsARequestIntoAHouseholdPlan = noClasses()
            .that().resideInAPackage("..controllers..")
            .and().doNotBelongToAnyOf(PlanScopes.class)
            .should().callMethod(PlanScope.class, "of", Household.class)
            .orShould().callConstructor(PlanScope.OfHousehold.class, Household.class)
            .because("PlanScopes is where membership of the household is verified");

    @ArchTest
    static final ArchRule householdEndpointsGoWhenHouseholdsAreOff = classes()
            .that().resideInAPackage("..controllers.households..")
            .and().areAnnotatedWith(RestController.class)
            .should().beAnnotatedWith(ConditionalOnHouseholdsEnabled.class);

    @ArchTest
    static final ArchRule adminEndpointsRequireTheAdminRole = classes()
            .that().resideInAPackage("..controllers.admin..")
            .and().areAnnotatedWith(RestController.class)
            .should().beAnnotatedWith(DescribedPredicate.describe("@PreAuthorize(\"hasAuthority('ADMIN')\")",
                    (JavaAnnotation<?> annotation) -> annotation.getRawType().isEquivalentTo(PreAuthorize.class)
                            && "hasAuthority('ADMIN')".equals(annotation.get("value").orElse(null))));

    @ArchTest
    static final ArchRule endpointsReturnResponseClasses = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controllers..")
            .and().areMetaAnnotatedWith(RequestMapping.class)
            .should(returnOnlyWebLayerTypes())
            .because("entities and service types must not become part of the API by accident");

    private static ArchCondition<JavaMethod> returnOnlyWebLayerTypes() {
        return new ArchCondition<>("return nothing, raw content, or types of the web layer") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (!isAllowedBody(method.getReturnType())) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " returns " + method.getReturnType().getName()));
                }
            }
        };
    }

    private static boolean isAllowedBody(JavaType type) {
        var raw = type.toErasure();
        if (raw.isEquivalentTo(void.class)) {
            return true;
        }
        var isWrapper = raw.isEquivalentTo(ResponseEntity.class) || raw.isAssignableTo(Collection.class);
        if (!isWrapper) {
            return raw.getPackageName().startsWith(WEB_LAYER);
        }
        if (!(type instanceof JavaParameterizedType parameterized)) {
            return false;
        }
        var body = parameterized.getActualTypeArguments().get(0);
        // Raw content carries its own content type, which only a ResponseEntity can set.
        var isRawContent = body.toErasure().isEquivalentTo(Void.class)
                || body.toErasure().isEquivalentTo(byte[].class) || body.toErasure().isEquivalentTo(String.class);
        return (raw.isEquivalentTo(ResponseEntity.class) && isRawContent) || isAllowedBody(body);
    }
}
