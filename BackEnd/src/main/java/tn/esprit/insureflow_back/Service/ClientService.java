package tn.esprit.insureflow_back.Service;


import tn.esprit.insureflow_back.Domain.Entities.Client;
import tn.esprit.insureflow_back.Repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    public Client creerClient(Client client) {
        return clientRepository.save(client);
    }

    public List<Client> tousLesClients() {
        return clientRepository.findAll();
    }

    public Client getClientById(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client introuvable : " + id));
    }
}