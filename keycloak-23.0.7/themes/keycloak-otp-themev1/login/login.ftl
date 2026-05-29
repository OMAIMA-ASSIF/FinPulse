
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username') displayInfo=false; section>

<#if section = "header">
    <title>Connexion - FasoRanana</title>
<#elseif section = "form">
<style>
    html, body {
        margin: 0;
        padding: 0;
        font-family: Inter, sans-serif;
        background: #f5f7fa;
        color: #4E5562;
        height: 100%;
        overflow: hidden;
        font-size:0px;
    }
    .login-container {
        display: flex;
        min-height: 100vh;
        width: 100%;
        box-sizing: border-box;
    }
    
    #username-email{
        width:330px;
    }
    #country-code{
        color: #4E5562;
        width:150px;
    }
    .alert-error {
        background: #ffe0e0;
        border: 1px solid #ffcccc;
        padding: 12px;
        border-radius: 6px;
        color: #b30000;
        font-size: 14px;
        margin-bottom: 15px;
    }

    a{
        font-weight: 600;
    }

    .login-left {
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: flex-start;
        padding: 60px;
        padding-left: 110px;
        background: #fff;
        position: relative;
    }
    .login-page{
        width:640px;
        margin-top: 100px;
        height: auto;
        display: flex;
        flex-direction: column;
        gap: 10px;
    }
    .login-right {
        flex: 1;
        background: white;
        display: flex;
        align-items: center;
        justify-content: center;
        max-width: 100%;
    }
    .login-right img {
        max-width: 95%;
    }
    .login-div{
        width:650px;
        background:linear-gradient(-90deg, #accbee 0%, #e7f0fd 100%);
        border-radius: 20px;
        margin-right: 150px;
    }
    .logo {
        margin-bottom: 40px;
        margin-top: 0;
        padding-top: 0;
    }
    .logo img {
        height: 50px;
        display: block;
        margin-top: -20px;
        width:200px;
    }
    .help-link{
        margin-bottom:30px;
    }
    label {
        font-size: 13px;
        color:#4E5562;
    }
    .form-group{
        width:360px;
        margin-bottom: 20px;
    }
    .form-title {
        font-size: 28px;
        font-weight: 600;
        color: #181D25;
        margin-bottom: 10px;
    }
    .form-subtitle {
        margin-bottom: 30px;
        font-size: 14px;
    }
    .form-subtitle a {
        color: #4E5562;
    }
    
    .radio-group {
        display: flex;
        gap: 20px;
        margin-bottom: 20px;
    }
    .radio-group label {
        display: flex;
        align-items: center;
        cursor: pointer;
    }
    .radio-group input {
        margin-right: 8px;
    }
    .form-control {
        width: 100%;
        padding: 12px 14px;
        border: 1px solid #ccc;
        border-radius: 8px;
        font-size: 16px;
    }
    .country-select {
        display: flex;
        gap: 10px;
    }
    .country-select select {
        width: 120px;
    }
    .btn-primary {
        background: #0033cc;
        color: #fff;
        padding: 14px;
        border: none;
        border-radius: 8px;
        width: 100%;
        font-size: 16px;
        font-weight: 600;
        cursor: pointer;
        transition: 0.3s;
    }
    a:hover {
      color: rgb(221, 118, 34)!important;
    }
    .btn-primary:hover {
        background: #001f80;
    }
    .help-link a {
        font-size: 13px;
        color: #4E5562;
    }
    footer {
        margin-top: 100px;
        padding:0px;
        font-size: 13px;
        color: #4E5562;
    }
    
    .futurion{
        color: #4E5562;
        font-weight: 700px!important;
        text-decoration:none;
    }
    @media(max-width: 768px) {
        .login-container {
            flex-direction: column;
        }
        .login-right {
            display: none;
        }
        .login-left {
            padding: 40px 20px;
        }
    .login-container a:hover,
        .login-left a:hover,
        .login-right a:hover,
        a:hover {
            color: rgb(221, 118, 34) !important;
        }
    }
</style>

<div class="login-container">
    <!-- Left Panel -->
    <div class="login-left">
        <div class="logo">
            <img src="${url.resourcesPath}/img/logo.png" alt="FasoRanana Logo" />
        </div>
        <div class="login-page">
            <div class="form-title">Heureux de vous revoir</div>
            <div class="form-subtitle">
                Vous n'avez pas de compte ? <a href="${url.registrationUrl}">Créer un compte</a>
            </div>

            <form id="kc-form-login" action="${url.loginAction}" method="post">
                <div class="radio-group">
                    <label>
                        <input type="radio" name="loginMethod" value="phone" checked> Téléphone
                    </label>
                    <label>
                        <input type="radio" name="loginMethod" value="email"> Email
                    </label>
                </div>

                <div id="input-container">
                    <!-- Téléphone -->
                    <div id="phone-input" class="form-group country-select">
                        <select id="country-code" class="form-control">
                            <option value="+226" selected>BF (+226)</option>
                            <option value="+223">ML (+223)</option>
                            <option value="+225">CI (+225)</option>
                        </select>
                        <input type="text" id="username-phone" name="username" class="form-control" placeholder="Votre numéro" value="${(param.username)!}" autocomplete="off"/>
                    </div>
                    <!-- Email -->
                    <div id="email-input" class="form-group" style="display:none;">
                        <input type="email" id="username-email" name="username" class="form-control" placeholder="Votre adresse email" value="${(param.username)!}" autocomplete="off"/>
                    </div>
                </div>

                <div class="form-group">
                    <input type="submit" id="kc-login" class="btn-primary" value="Envoyer le code de vérification" />
                </div>
            </form>
        </div>

        <footer>
            <div class="help-link">
                 <a href="#">Besoin d'aide ?</a>
            </div>
            <p>© Tous droits réservés. Fabriqué par <a href="https://futurion.tech" target="_blank" class="futurion">Futurion</a></p>
        </footer>
    </div>

    <!-- Right Panel -->
    <div class="login-right">
        <div class="login-div">
            <img src="${url.resourcesPath}/img/bg-side.png" alt="Femme heureuse avec un téléphone">
        </div>
    </div>
</div>


<script>
    const phoneRadio = document.querySelector('input[value="phone"]');
    const emailRadio = document.querySelector('input[value="email"]');
    const phoneInputDiv = document.getElementById('phone-input');
    const emailInputDiv = document.getElementById('email-input');
    const phoneInput = document.getElementById('username-phone');
    const emailInput = document.getElementById('username-email');

    function updateInputs() {
        if (phoneRadio.checked) {
            phoneInputDiv.style.display = 'flex';
            emailInputDiv.style.display = 'none';
            phoneInput.setAttribute("name", "username");
            emailInput.removeAttribute("name");
        } else if (emailRadio.checked) {
            phoneInputDiv.style.display = 'none';
            emailInputDiv.style.display = 'block';
            emailInput.setAttribute("name", "username");
            phoneInput.removeAttribute("name");
        }
    }

    phoneRadio.addEventListener('change', updateInputs);
    emailRadio.addEventListener('change', updateInputs);

    // Initialisation au chargement
    updateInputs();
</script>


<#elseif section = "info">
</#if>
</@layout.registrationLayout>
