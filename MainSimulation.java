import java.util.*;
import java.io.*;

public class MainSimulation {
    public static void main(String[] args) {
        EntiteTransport et = new EntiteTransport();
        EntiteReseau er = new EntiteReseau();

        // Lancement des threads
        Thread threadET = new Thread(() -> et.executer());
        Thread threadER = new Thread(() -> er.executer());

        threadET.start();
        threadER.start();
    }
}

class EntiteTransport {
    private Map<Integer, EtatConnexion> tableConnexions = new HashMap<>();
    private String fichierLecture = "S_lec.txt";
    private String fichierEcriture = "S_ecr.txt";
    private int idCourant = 0;

    public void executer() {
        while (true) {
            String demande = FichierInterface.lireDepuisFichier(fichierLecture);
            if (demande == null || demande.isEmpty()) continue;

            int adresseSrc = genererAdresse();
            int adresseDst = genererAdresse();
            while (adresseDst == adresseSrc) {
                adresseDst = genererAdresse();
            }

            int idConnexion = idCourant++;
            tableConnexions.put(idConnexion, EtatConnexion.EN_ATTENTE);

            Message message = new Message(PrimitiveType.N_CONNECT_req, idConnexion, adresseSrc, adresseDst, "DEMANDE_CONNEXION");
            ecrireVersReseau(message);

            // Attente d'une réponse : conf ou ind
            String reponse = FichierInterface.lireDepuisFichier(fichierEcriture);
            if (reponse == null) continue;

            Message msgReponse = Message.fromString(reponse);
            if (msgReponse.type == PrimitiveType.N_CONNECT_conf) {
                tableConnexions.put(idConnexion, EtatConnexion.ETABLIE);
                // Envoi des données
                for (int i = 0; i < 3; i++) {
                    Message dataMsg = new Message(PrimitiveType.DATA_req, idConnexion, adresseSrc, adresseDst, "DATA_" + i);
                    ecrireVersReseau(dataMsg);
                }

                // Demande de libération
                Message fin = new Message(PrimitiveType.N_DISCONNECT_req, idConnexion, adresseSrc, adresseDst, "FIN_CONNEXION");
                ecrireVersReseau(fin);
            } else {
                tableConnexions.put(idConnexion, EtatConnexion.REFUSEE);
            }
        }
    }

    public void ecrireVersReseau(Message message) {
        FichierInterface.ecrireDansFichier("ER_in.txt", message.toString());
    }

    private int genererAdresse() {
        return new Random().nextInt(255);
    }
}

class EntiteReseau {
    private Map<Integer, Connexion> connexions = new HashMap<>();
    private String fichierLiaisonEcriture = "L_ecr.txt";
    private String fichierLiaisonLecture = "L_lec.txt";
    private int numeroConnexion = 0;

    public void executer() {
        while (true) {
            String contenu = FichierInterface.lireDepuisFichier("ER_in.txt");
            if (contenu == null || contenu.isEmpty()) continue;

            Message message = Message.fromString(contenu);
            switch (message.type) {
                case N_CONNECT_req:
                    gererConnexion(message);
                    break;
                case DATA_req:
                    gererData(message);
                    break;
                case N_DISCONNECT_req:
                    gererDeconnexion(message);
                    break;
                default: ;
            }
        }
    }

    public void gererConnexion(Message message) {
        int idConnexion = message.idConnexion;
        int src = message.adresseSrc;
        int dst = message.adresseDst;

        if (src % 27 == 0) {
            Message rejet = new Message(PrimitiveType.N_DISCONNECT_ind, idConnexion, src, dst, "REFUSE_ER");
            FichierInterface.ecrireDansFichier("S_ecr.txt", rejet.toString());
            return;
        }

        Connexion c = new Connexion(numeroConnexion++, src, dst);
        connexions.put(idConnexion, c);

        FichierInterface.ecrireDansFichier(fichierLiaisonEcriture, "PAQUET_APPEL:" + idConnexion);

        String reponse = LiaisonSimulee.simulerReceptionConnexion(src);
        if (reponse.equals("NO_RESPONSE") || reponse.equals("REFUSE")) {
            Message rejet = new Message(PrimitiveType.N_DISCONNECT_ind, idConnexion, src, dst, reponse);
            FichierInterface.ecrireDansFichier("S_ecr.txt", rejet.toString());
        } else {
            Message confirmation = new Message(PrimitiveType.N_CONNECT_conf, idConnexion, src, dst, "ACCEPT");
            connexions.get(idConnexion).etat = EtatConnexion.ETABLIE;
            FichierInterface.ecrireDansFichier("S_ecr.txt", confirmation.toString());
        }
    }

    public void gererData(Message message) {
        int id = message.idConnexion;
        Connexion c = connexions.get(id);
        if (c == null || c.etat != EtatConnexion.ETABLIE) return;

        FichierInterface.ecrireDansFichier(fichierLiaisonEcriture, "DATA:" + message.contenu);

        String reponse = LiaisonSimulee.simulerReceptionData(c.adresseSource, c.numeroPaquet++);
        FichierInterface.ecrireDansFichier(fichierLiaisonLecture, id + ":" + reponse);
    }

    public void gererDeconnexion(Message message) {
        int id = message.idConnexion;
        Connexion c = connexions.remove(id);
        if (c != null) {
            FichierInterface.ecrireDansFichier(fichierLiaisonEcriture, "FIN_CONNEXION:" + id);
            FichierInterface.ecrireDansFichier("S_ecr.txt", new Message(PrimitiveType.N_DISCONNECT_ind, id, c.adresseSource, c.adresseDestination, "LIBEREE").toString());
        }
    }
}

class Connexion {
    int idConnexion;
    int adresseSource;
    int adresseDestination;
    EtatConnexion etat;
    int numeroPaquet;

    public Connexion(int id, int src, int dst) {
        this.idConnexion = id;
        this.adresseSource = src;
        this.adresseDestination = dst;
        this.etat = EtatConnexion.EN_ATTENTE;
        this.numeroPaquet = 0;
    }
}

enum EtatConnexion {
    EN_ATTENTE,
    ETABLIE,
    REFUSEE,
    LIBEREE
}

class Message {
    PrimitiveType type;
    int idConnexion;
    int adresseSrc;
    int adresseDst;
    String contenu;

    public Message(PrimitiveType type, int id, int src, int dst, String contenu) {
        this.type = type;
        this.idConnexion = id;
        this.adresseSrc = src;
        this.adresseDst = dst;
        this.contenu = contenu;
    }

    @Override
    public String toString() {
        return type + "," + idConnexion + "," + adresseSrc + "," + adresseDst + "," + contenu;
    }

    public static Message fromString(String ligne) {
        String[] parts = ligne.split(",");
        return new Message(
            PrimitiveType.valueOf(parts[0]),
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2]),
            Integer.parseInt(parts[3]),
            parts[4]
        );
    }
}

enum PrimitiveType {
    N_CONNECT_req,
    N_CONNECT_conf,
    N_DISCONNECT_req,
    N_DISCONNECT_ind,
    DATA_req,
    DATA_ind
}

class LiaisonSimulee {
    public static String simulerReceptionConnexion(int adresseSource) {
        if (adresseSource % 19 == 0) return "NO_RESPONSE";
        if (adresseSource % 13 == 0) return "REFUSE";
        return "ACCEPT";
    }

    public static String simulerReceptionData(int adresseSource, int numeroPaquet) {
        if (adresseSource % 15 == 0) return "NO_RESPONSE";
        if (numeroPaquet == new Random().nextInt(8)) return "NAK";
        return "ACK";
    }
}

class FichierInterface {
    public static synchronized void ecrireDansFichier(String fichier, String contenu) {
        try (FileWriter fw = new FileWriter(fichier, true); BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(contenu);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized String lireDepuisFichier(String fichier) {
        File f = new File(fichier);
        if (!f.exists()) return null;

        List<String> lignes = new ArrayList<>();
        String ligneLue = null;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                lignes.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (lignes.isEmpty()) return null;

        ligneLue = lignes.remove(0);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f, false))) {
            for (String l : lignes) {
                bw.write(l);
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ligneLue;
    }
}
