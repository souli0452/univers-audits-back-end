<!DOCTYPE html>
<html>
<body style="font-family: Arial, sans-serif; color: #333;">
    <p>Bonjour,</p>
    <p>Un compte a été créé pour vous sur la plateforme <strong>INTEGRITE +</strong>.</p>
    <p>Cliquez sur le bouton ci-dessous pour définir votre mot de passe :</p>
    <p>
        <a href="${link}"
           style="background-color:#1a73e8; color:white; padding:10px 20px;
                  text-decoration:none; border-radius:4px;">
            Définir mon mot de passe
        </a>
    </p>
    <p>Ce lien expire dans <strong>${linkExpirationFormatter(linkExpiration)}</strong>.</p>
    <p>Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.</p>
    <br>
    <p>Cordialement,<br><strong>L'équipe ASCE-LC</strong></p>
</body>
</html>