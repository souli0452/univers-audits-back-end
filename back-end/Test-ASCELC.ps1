$KEYCLOAK_URL = "http://localhost:8080"
$BASE_URL     = "http://localhost:8081"
$REALM        = "asce-lc"
$CLIENT_ID    = "asce-lc-frontend"
$USERNAME     = "admin.asce"
$PASSWORD     = "Admin1234!"

$AGENT_ID     = "484e681b-99e8-4fdf-a578-8056fa2d3845"

$DOSSIER_ID       = $null
$ACCESS_CODE      = $null
$DOSSIER_VER      = $null
$INVESTIGATION_ID = $null
$TOKEN            = $null
$script:errors    = 0
$script:warnings  = 0

function Write-Header($title) {
    Write-Host ""
    Write-Host "================================================" -ForegroundColor Cyan
    Write-Host "  $title" -ForegroundColor Cyan
    Write-Host "================================================" -ForegroundColor Cyan
}

function Write-Step($step, $label) {
    Write-Host ""
    Write-Host "  [$step] $label" -ForegroundColor Yellow
    Write-Host "  ----------------------------------------------" -ForegroundColor DarkGray
}

function Write-OK($msg) {
    Write-Host "  [OK] $msg" -ForegroundColor Green
}

function Write-FAIL($msg) {
    Write-Host "  [FAIL] $msg" -ForegroundColor Red
    $script:errors++
}

function Write-WARN($msg) {
    Write-Host "  [WARN] $msg" -ForegroundColor Magenta
    $script:warnings++
}

function Write-Info($label, $value) {
    Write-Host "     $label : $value" -ForegroundColor White
}

function Invoke-API {
    param(
        [string]$Method,
        [string]$Uri,
        [string]$Body  = $null,
        [string]$Token = $null
    )

    $headers = @{ "Content-Type" = "application/json; charset=utf-8" }
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    try {
        $params = @{
            Method      = $Method
            Uri         = $Uri
            Headers     = $headers
            ErrorAction = "Stop"
        }
        if ($Body) {
            $params["Body"] = [System.Text.Encoding]::UTF8.GetBytes($Body)
        }
        $response = Invoke-RestMethod @params
        return $response
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        $raw        = $_.ErrorDetails.Message
        Write-FAIL "HTTP $statusCode sur $Uri"
        if ($raw) {
            try {
                $errJson = $raw | ConvertFrom-Json
                Write-Host "     Code    : $($errJson.code)" -ForegroundColor DarkRed
                Write-Host "     Message : $($errJson.message)" -ForegroundColor DarkRed
            }
            catch {
                Write-Host "     Reponse : $raw" -ForegroundColor DarkRed
            }
        }
        return $null
    }
}

Write-Header "0. AUTHENTIFICATION KEYCLOAK"
Write-Step "0.1" "Obtenir le token JWT"

try {
    $authBody = "grant_type=password&client_id=$CLIENT_ID&username=$USERNAME&password=$PASSWORD"
    $authResponse = Invoke-RestMethod `
        -Method POST `
        -Uri "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body $authBody `
        -ErrorAction Stop

    $TOKEN = $authResponse.access_token

    $parts = $TOKEN -split "\."
    $pad   = $parts[1].Length % 4
    if ($pad -ne 0) { $parts[1] += "=" * (4 - $pad) }
    $payload = [System.Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String($parts[1])) | ConvertFrom-Json
    $roles = $payload.realm_access.roles

    Write-OK "Token obtenu"
    Write-OK "Roles dans le token :"
    foreach ($r in $roles) {
        Write-Host "     -> $r" -ForegroundColor DarkCyan
    }

    $requiredRoles = @("AGENT_BRPD", "CGE", "CGEA", "ADMIN_DDIC")
    foreach ($r in $requiredRoles) {
        if ($roles -contains $r) { Write-OK "Role $r present" }
        else { Write-WARN "Role $r ABSENT du token" }
    }
}
catch {
    Write-FAIL "Impossible d obtenir le token : $_"
    exit 1
}

Write-Header "1. SOUMISSION DOSSIER PUBLIC"
Write-Step "1.1" "POST /api/v1/dossiers/public/submit"

$submitBody = '{"type":"COMPLAINT","submissionMode":"IN_PERSON","object":"Detournement de fonds a la mairie de Koudougou","description":"Le maire a detourne 50 millions FCFA du budget communal en 2024.","incidentLocation":"Mairie de Koudougou","incidentPeriod":"Janvier-Decembre 2024","estimatedLoss":50000000,"isConfidential":false,"declarantData":{"typeDeclarant":"CITIZEN","firstName":"Moussa","lastName":"Ouedraogo","email":"moussa.ouedraogo@test.bf","phoneNumber":"+22670000001","address":"Secteur 5, Koudougou","commune":"Koudougou","province":"Boulkiemde","profession":"Commercant","anonymous":false,"dataProcessingConsent":true,"notificationsAccepted":true}}'

$dossier = Invoke-API -Method POST `
    -Uri "$BASE_URL/api/v1/dossiers/public/submit" `
    -Body $submitBody

if ($null -eq $dossier) {
    Write-FAIL "Soumission echouee - arret du script"
    exit 1
}

if ($dossier.status -eq "SOUMIS") { Write-OK "Statut = SOUMIS" }
else { Write-FAIL "Statut attendu SOUMIS, obtenu : $($dossier.status)" }

if ($dossier.id)         { Write-OK "ID present" }         else { Write-FAIL "ID manquant" }
if ($dossier.accessCode) { Write-OK "AccessCode present" } else { Write-FAIL "AccessCode manquant" }

$DOSSIER_ID  = $dossier.id
$ACCESS_CODE = $dossier.accessCode
$DOSSIER_VER = $dossier.version

Write-Info "ID"         $DOSSIER_ID
Write-Info "AccessCode" $ACCESS_CODE
Write-Info "Version"    $DOSSIER_VER

Write-Header "2. WORKFLOW BRPD"
Write-Step "2.1" "PATCH /register - SOUMIS -> RECU"

$registerBody = "{`"version`":$DOSSIER_VER,`"reason`":`"Dossier recu au guichet BRPD. Pieces d identite verifiees.`"}"

$registered = Invoke-API -Method PATCH `
    -Uri "$BASE_URL/api/v1/dossiers/$DOSSIER_ID/register" `
    -Body $registerBody `
    -Token $TOKEN

if ($registered) {
    if ($registered.status -eq "RECU") { Write-OK "Statut = RECU" }
    else { Write-FAIL "Statut attendu RECU, obtenu : $($registered.status)" }

    if ($registered.number)                 { Write-OK "Numero : $($registered.number)" }
    else { Write-FAIL "Numero officiel manquant" }

    if ($registered.receptionDate)          { Write-OK "receptionDate present" }          else { Write-WARN "receptionDate manquant" }
    if ($registered.acknowledgmentDeadline) { Write-OK "acknowledgmentDeadline present" } else { Write-WARN "acknowledgmentDeadline manquant" }

    $DOSSIER_VER = $registered.version
    Write-Info "Numero"  $registered.number
    Write-Info "Version" $DOSSIER_VER
}

Write-Step "2.2" "GET /public/track/:code - Suivi citoyen (sans token)"

$suivi = Invoke-API -Method GET `
    -Uri "$BASE_URL/api/v1/dossiers/public/track/$ACCESS_CODE"

if ($suivi) {
    if ($suivi.status -eq "RECU") { Write-OK "Citoyen voit statut RECU" }
    else { Write-WARN "Statut citoyen : $($suivi.status)" }
    if ($suivi.number) { Write-OK "Numero visible : $($suivi.number)" }
    else { Write-WARN "Numero non visible" }
}

Write-Header "3. WORKFLOW CONSEILLER JURIDIQUE"
Write-Step "3.1" "PATCH /start-study - RECU -> EN_ETUDE_OPPORTUNITE"

$studyBody = "{`"version`":$DOSSIER_VER,`"reason`":`"Dossier transmis au conseiller juridique pour etude.`"}"

$studied = Invoke-API -Method PATCH `
    -Uri "$BASE_URL/api/v1/dossiers/$DOSSIER_ID/start-study" `
    -Body $studyBody `
    -Token $TOKEN

if ($studied) {
    if ($studied.status -eq "EN_ETUDE_OPPORTUNITE") { Write-OK "Statut = EN_ETUDE_OPPORTUNITE" }
    else { Write-FAIL "Statut attendu EN_ETUDE_OPPORTUNITE, obtenu : $($studied.status)" }
    $DOSSIER_VER = $studied.version
    Write-Info "Version" $DOSSIER_VER
}

Write-Header "4. WORKFLOW CTADP"
Write-Step "4.1" "PATCH /submit-ctadp - EN_ETUDE_OPPORTUNITE -> EN_REVUE_CTADP"

$ctadpBody = "{`"version`":$DOSSIER_VER,`"reason`":`"Faits graves - soumis au CTADP pour avis.`"}"

$ctadp = Invoke-API -Method PATCH `
    -Uri "$BASE_URL/api/v1/dossiers/$DOSSIER_ID/submit-ctadp" `
    -Body $ctadpBody `
    -Token $TOKEN

if ($ctadp) {
    if ($ctadp.status -eq "EN_REVUE_CTADP") { Write-OK "Statut = EN_REVUE_CTADP" }
    else { Write-FAIL "Statut attendu EN_REVUE_CTADP, obtenu : $($ctadp.status)" }
    $DOSSIER_VER = $ctadp.version
    Write-Info "Version" $DOSSIER_VER
}

Write-Header "5. WORKFLOW CGE - DECISION RECEVABILITE"
Write-Step "5.1" "PATCH /declare-admissible - EN_REVUE_CTADP -> RECEVABLE"

$admissibleBody = "{`"version`":$DOSSIER_VER,`"reason`":`"CTADP recommande l investigation. Faits graves. 50 millions FCFA.`"}"

$admissible = Invoke-API -Method PATCH `
    -Uri "$BASE_URL/api/v1/dossiers/$DOSSIER_ID/declare-admissible" `
    -Body $admissibleBody `
    -Token $TOKEN

if ($admissible) {
    if ($admissible.status -eq "RECEVABLE") { Write-OK "Statut = RECEVABLE" }
    else { Write-FAIL "Statut attendu RECEVABLE, obtenu : $($admissible.status)" }
    if ($admissible.eligibilityDecisionDate) { Write-OK "eligibilityDecisionDate present" }
    else { Write-WARN "eligibilityDecisionDate manquant" }
    $DOSSIER_VER = $admissible.version
    Write-Info "Version" $DOSSIER_VER
}

Write-Header "6. INVESTIGATION"
Write-Step "6.1" "POST /investigations/dossier/:id/open"

$investBody = '{"plannedDurationDays":90,"notes":"Investigation ouverte suite a decision CGE."}'

$investigation = Invoke-API -Method POST `
    -Uri "$BASE_URL/api/v1/investigations/dossier/$DOSSIER_ID/open" `
    -Body $investBody `
    -Token $TOKEN

if ($investigation) {
    if ($investigation.status -eq "INITIATED") { Write-OK "Statut = INITIATED" }
    else { Write-FAIL "Statut attendu INITIATED, obtenu : $($investigation.status)" }
    if ($investigation.plannedDurationDays -eq 90) { Write-OK "Duree 90 jours confirmee" }
    $INVESTIGATION_ID = $investigation.id
    Write-Info "Investigation ID" $INVESTIGATION_ID
}

if ($INVESTIGATION_ID) {
    Write-Step "6.2" "POST /investigations/:id/members - Ajouter TEAM_LEADER"


    $memberBody = "{`"agentId`":`"$AGENT_ID`",`"teamRole`":`"TEAM_LEADER`"}"

    $member = Invoke-API -Method POST `
        -Uri "$BASE_URL/api/v1/investigations/$INVESTIGATION_ID/members" `
        -Body $memberBody `
        -Token $TOKEN

    if ($member) {
        Write-OK "Membre TEAM_LEADER ajoute"
        Write-Info "Investigation statut" $member.status
    }
    else {
        Write-WARN "Ajout membre echoue - le start risque d echouer"
    }

    Write-Step "6.3" "PATCH /investigations/:id/start - INITIATED -> IN_PROGRESS"

    $started = Invoke-API -Method PATCH `
        -Uri "$BASE_URL/api/v1/investigations/$INVESTIGATION_ID/start" `
        -Token $TOKEN

    if ($started) {
        if ($started.status -eq "IN_PROGRESS") { Write-OK "Statut = IN_PROGRESS" }
        else { Write-FAIL "Statut attendu IN_PROGRESS, obtenu : $($started.status)" }
        if ($started.startDate)      { Write-OK "startDate present" }      else { Write-WARN "startDate manquant" }
        if ($started.plannedEndDate) { Write-OK "plannedEndDate present" }  else { Write-WARN "plannedEndDate manquant" }
        Write-Info "Demarrage"  $started.startDate
        Write-Info "Fin prevue" $started.plannedEndDate
    }
}


Write-Header "7. NOTIFICATIONS"
Write-Step "7.1" "GET /notifications/dossier/:id"

$notifs = Invoke-API -Method GET `
    -Uri "$BASE_URL/api/v1/notifications/dossier/$DOSSIER_ID" `
    -Token $TOKEN

if ($notifs) {
    $content = $notifs.content
    if ($content) {
        $types = $content | ForEach-Object { $_.type }
        Write-OK "Notifications recues :"
        foreach ($t in $types) { Write-Host "     -> $t" -ForegroundColor DarkCyan }
        if ($types -contains "RECEIPT_B4")        { Write-OK "RECEIPT_B4 present" }        else { Write-WARN "RECEIPT_B4 manquant" }
        if ($types -contains "ACKNOWLEDGMENT_B5") { Write-OK "ACKNOWLEDGMENT_B5 present" } else { Write-WARN "ACKNOWLEDGMENT_B5 manquant" }
    }
    else {
        Write-WARN "Aucune notification dans content"
    }
}

# ================================================================
#  ETAPE 8 - STATISTIQUES
# ================================================================
Write-Header "8. STATISTIQUES - DASHBOARD"
Write-Step "8.1" "GET /stats/dashboard"

$statsUrl = $BASE_URL + "/api/v1/stats/dashboard?start=2026-01-01T00:00:00Z" + "&end=2026-12-31T23:59:59Z"

$stats = Invoke-API -Method GET -Uri $statsUrl -Token $TOKEN

if ($stats) {
    if ($null -ne $stats.totalDossiers)     { Write-OK "totalDossiers : $($stats.totalDossiers)" }
    else { Write-FAIL "totalDossiers manquant" }
    if ($null -ne $stats.countByStatus)     { Write-OK "countByStatus present" }
    else { Write-FAIL "countByStatus manquant" }
    if ($null -ne $stats.admissibilityRate) { Write-OK "admissibilityRate : $($stats.admissibilityRate)%" }
    else { Write-WARN "admissibilityRate manquant" }
}


Write-Header "9. TEST TRANSITION INVALIDE (attendu : 400)"
Write-Step "9.1" "PATCH /register sur dossier deja avance"

$invalidBody = "{`"version`":$DOSSIER_VER,`"reason`":`"Test transition invalide`"}"
$headers400  = @{
    "Content-Type"  = "application/json; charset=utf-8"
    "Authorization" = "Bearer $TOKEN"
}

try {
    Invoke-RestMethod `
        -Method PATCH `
        -Uri "$BASE_URL/api/v1/dossiers/$DOSSIER_ID/register" `
        -Headers $headers400 `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($invalidBody)) `
        -ErrorAction Stop

    Write-FAIL "Attendait 400 mais la requete a reussi - transition invalide non bloquee !"
}
catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 400) {
        Write-OK "400 Bad Request recu - transition invalide correctement refusee"
    }
    else {
        Write-WARN "Code HTTP recu : $code (attendu 400)"
    }
}


Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  RESUME FINAL" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Info "Dossier ID"       $DOSSIER_ID
Write-Info "Access Code"      $ACCESS_CODE
Write-Info "Investigation ID" $INVESTIGATION_ID
Write-Host ""

if ($script:errors -eq 0 -and $script:warnings -eq 0) {
    Write-Host "  [OK] TOUS LES TESTS REUSSIS SANS AVERTISSEMENT" -ForegroundColor Green
}
elseif ($script:errors -eq 0) {
    Write-Host "  [OK] Aucune erreur | [WARN] $($script:warnings) avertissement(s)" -ForegroundColor Yellow
}
else {
    Write-Host "  [FAIL] $($script:errors) erreur(s) | [WARN] $($script:warnings) avertissement(s)" -ForegroundColor Red
}
Write-Host ""