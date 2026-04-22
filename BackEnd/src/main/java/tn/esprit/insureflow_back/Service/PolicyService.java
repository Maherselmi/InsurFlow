package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.Domain.Entities.Client;
import tn.esprit.insureflow_back.Domain.Entities.Policy;
import tn.esprit.insureflow_back.Repository.ClientRepository;
import tn.esprit.insureflow_back.Repository.PolicyRepository;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final ClientRepository clientRepository;

    public Policy createPolicy(Policy policy) {
        if (policy == null) {
            throw new RuntimeException("Policy is required");
        }

        if (policy.getClient() == null || policy.getClient().getId() == null) {
            throw new RuntimeException("Client is required");
        }

        if (policy.getPolicyNumber() == null || policy.getPolicyNumber().isBlank()) {
            throw new RuntimeException("Policy number is required");
        }

        if (policy.getType() == null || policy.getType().isBlank()) {
            throw new RuntimeException("Policy type is required");
        }

        if (policy.getStartDate() == null || policy.getEndDate() == null) {
            throw new RuntimeException("Policy startDate and endDate are required");
        }

        if (policy.getStartDate().isAfter(policy.getEndDate())) {
            throw new RuntimeException("Policy startDate cannot be after endDate");
        }

        Long clientId = policy.getClient().getId();

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        policy.setClient(client);
        policy.setType(policy.getType().trim().toUpperCase(Locale.ROOT));

        if (policy.getFormule() != null) {
            policy.setFormule(policy.getFormule().trim().toUpperCase(Locale.ROOT));
        }

        if (policy.getProductCode() != null) {
            policy.setProductCode(policy.getProductCode().trim().toUpperCase(Locale.ROOT));
        }

        policy.setPolicyNumber(policy.getPolicyNumber().trim().toUpperCase(Locale.ROOT));

        return policyRepository.save(policy);
    }

    public List<Policy> getAllPolicies() {
        return policyRepository.findAll();
    }

    public Policy getPolicyById(Long id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
    }
}