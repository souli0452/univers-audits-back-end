<!DOCTYPE html>
<html>
<body style="font-family: Arial, sans-serif; color: #333;">
    <p>Bonjour,</p>
    <p>Nous avons reçu une demande de réinitialisation du mot de passe de votre compte <strong>ASCE-LC</strong>.</p>
    <p>Pour choisir un nouveau mot de passe, cliquez sur le bouton ci-dessous :</p>
    <p>
        <a href="${link}"
           style="background-color:#009640; color:white; padding:10px 20px;
                  text-decoration:none; border-radius:4px;">
            Réinitialiser mon mot de passe
        </a>
    </p>
    <p>Ce lien expire dans <strong>${linkExpirationFormatter(linkExpiration)}</strong> et ne peut être utilisé qu'une seule fois.</p>
    <p>Vous n'êtes pas à l'origine de cette demande ? Ignorez simplement ce message : votre mot de passe actuel reste inchangé.</p>
    <br>
    <p>Cordialement,<br><strong>L'équipe ASCE-LC</strong></p>
</body>
</html>
