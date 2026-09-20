package com.sterul.opencookbookapiserver.services.planning;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.planning.PlanDraft;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;
import com.sterul.opencookbookapiserver.repositories.PlanDraftRepository;
import com.sterul.opencookbookapiserver.repositories.PlanningProfileRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/** A cook's saved planning answers. */
@Service
@Slf4j
@Transactional
public class PlanningProfileService {

    private final PlanningProfileRepository profileRepository;
    private final PlanDraftRepository draftRepository;

    public PlanningProfileService(PlanningProfileRepository profileRepository, PlanDraftRepository draftRepository) {
        this.profileRepository = profileRepository;
        this.draftRepository = draftRepository;
    }

    public List<PlanningProfile> getProfiles(CookpalUser owner) {
        return profileRepository.findAllByOwnerOrderByName(owner);
    }

    /** Somebody else's profile is not found. */
    public PlanningProfile getProfile(Long id, CookpalUser owner) throws ElementNotFound {
        return profileRepository.findByIdAndOwner(id, owner).orElseThrow(ElementNotFound::new);
    }

    public PlanningProfile create(CookpalUser owner, PlanningProfile answers) {
        var profile = PlanningProfile.builder().owner(owner).build();
        copyAnswers(answers, profile);
        // The first profile is the one the wizard opens with, whatever the cook said.
        profile.setDefaultProfile(answers.isDefaultProfile() || profileRepository.findAllByOwnerOrderByName(owner).isEmpty());
        log.info("Creating planning profile '{}' for user {}", profile.getName(), owner.getUserId());
        return keepSingleDefault(profileRepository.save(profile));
    }

    public PlanningProfile update(Long id, CookpalUser owner, PlanningProfile answers) throws ElementNotFound {
        var profile = getProfile(id, owner);
        copyAnswers(answers, profile);
        profile.setDefaultProfile(answers.isDefaultProfile());
        return keepSingleDefault(profileRepository.save(profile));
    }

    /** Open drafts made from it are discarded: every adjustment to a draft is judged against its profile. */
    public void delete(Long id, CookpalUser owner) throws ElementNotFound {
        var profile = getProfile(id, owner);
        draftRepository.findAllByProfileAndStatus(profile, PlanDraft.Status.DRAFT)
                .forEach(draft -> draft.setStatus(PlanDraft.Status.DISCARDED));
        profileRepository.delete(profile);
    }

    /** Only one profile is the default; marking one takes the mark from the others. */
    private PlanningProfile keepSingleDefault(PlanningProfile marked) {
        if (marked.isDefaultProfile()) {
            profileRepository.findAllByOwnerOrderByName(marked.getOwner()).stream()
                    .filter(other -> !other.equals(marked) && other.isDefaultProfile())
                    .forEach(other -> other.setDefaultProfile(false));
        }
        return marked;
    }

    private static void copyAnswers(PlanningProfile from, PlanningProfile to) {
        to.setName(from.getName());
        to.setHouseholdSize(from.getHouseholdSize());
        to.setDiet(from.getDiet());
        to.setMeatMealsPerWeek(from.getMeatMealsPerWeek());
        to.setKcalPerDay(from.getKcalPerDay());
        to.setMacroStyle(from.getMacroStyle());
        to.setCooldownWeeks(from.getCooldownWeeks());
        to.setLeftoversAllowed(from.isLeftoversAllowed());
        to.setSpreadVariety(from.isSpreadVariety());
        to.setMeals(new ArrayList<>(from.getMeals()));
        to.setPantry(new ArrayList<>(from.getPantry()));
        to.setAvoidedIngredientIds(new HashSet<>(from.getAvoidedIngredientIds()));
    }
}
