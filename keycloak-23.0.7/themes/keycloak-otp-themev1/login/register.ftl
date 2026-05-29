<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('firstName','lastName','email','phoneNumber'); section>
<#if section = "header">
    <title>Inscription - FasoRanana</title>
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
     a:hover {
      color: rgb(221, 118, 34) !important;
    }
    #firstName, #lastName {
        width: 100%;
    }
    #countryCode{
        color: #4E5562;
        width: 150px;
    }
    .custom-checkbox {
        width: 15px;
        height: 20px;
        border-radius: 30%;
    }
    #email {
        width: 390px;
    }
    #kc-register-form {
        display: flex;
        flex-direction: column;
        gap: 20px;
    }
    #phoneNumber{
        width: 500px;
    }
    .conditions{
        color: #4E5562;
    }
    input[type="submit"]{
        width: 425px;
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
        margin-top: 40px;
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
    .form-row {
        display: flex;
        gap: 50px;
        width: 400px;
    }
    .form-group {
        width: 170px;
        margin-bottom: 0;
    }
    .form-title {
        font-size: 28px;
        font-weight: 600;
        color: #181D25;
        margin-bottom: 5px;
    }
    .form-subtitle {
        margin-bottom: 20px;
        font-size: 14px;
    }
    .form-subtitle a {
        color: #4E5562;
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
        width: 420px;
    }
    .country-select select {
        width: 150px;
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
    .btn-primary:hover {
        background: #001f80;
    }
    .help-link a {
        font-size: 13px;
        color: #4E5562;
    }
    footer {
        margin-top: 10px;
        font-size: 13px;
        color: #4E5562;
    }
    .futurion{
        color: #4E5562;
        font-weight: 700px !important;
        text-decoration: none;
    }
    .terms-checkbox {
        display: flex;
        align-items: center;
        margin-bottom: 20px;
        font-size: 13px;
    }
    .terms-checkbox input {
        margin-right: 8px;
    }
    a{
        font-weight: 550;
    }
    a:hover{
        color: rgb(221, 118, 34);
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
        .form-row, .country-select {
            width: 100%;
            flex-direction: column;
        }
        .form-row .form-group, .form-group {
            width: 100%;
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
                Vous avez déjà un compte ? <a href="${url.loginUrl}">Se connecter</a>
            </div>

            <form id="kc-register-form" action="${url.registrationAction}" method="post">
                
                <!-- Message d'erreur si aucun contact -->
                <div id="contact-error" style="color: red; display: none; font-size: 13px; margin-bottom: 10px;">
                    Veuillez renseigner au moins un numéro de téléphone ou une adresse e-mail.
                </div>
                
                <!-- Nom et Prénom -->
                <div class="form-row">
                    <div class="form-group">
                        <input type="text" id="lastName" name="lastName" class="form-control" placeholder="Entre votre nom" value="${(register.formData.lastName!'')}" required/>
                    </div>
                    <div class="form-group">
                        <input type="text" id="firstName" name="firstName" class="form-control" placeholder="Entrez votre prénom" value="${(register.formData.firstName!'')}" required/>
                    </div>
                </div>

                <!-- Téléphone -->
                <div class="country-select">
                    <select id="countryCode" name="user.attributes.countryCode" class="form-control">
                        <option value="+226" selected>BF (+226)</option>
                        <option value="+223">ML (+223)</option>
                        <option value="+225">CI (+225)</option>
                    </select>
                    <input type="tel" id="phoneNumber" name="user.attributes.phoneNumber" class="form-control" placeholder="Votre numéro" value="${(register.formData['user.attributes.phoneNumber']!'')}" />
                </div>

                <!-- Email (sans required) -->
                <div class="form-group">
                    <input type="email" id="email" name="email" class="form-control" placeholder="Entrez Votre adresse e-mail" value="${(register.formData.email!'')}" />
                </div>

                <!-- Conditions d'utilisation -->
                <div class="terms-checkbox">
                    <input type="checkbox" id="terms" class="custom-checkbox" required>
                    <label for="terms">
                        j'ai lu et j'accepte les <a href="/term-and-condition" class="conditions" target="_blank">Conditions d'utilisation</a>
                    </label>
                </div>
                
                <!-- Bouton d'inscription -->
                <div class="form-group">
                    <input type="submit" class="btn-primary" value="Créer mon compte"/>
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

<!-- Script validation -->
<script>
    document.getElementById('kc-register-form').addEventListener('submit', function(event) {
        const phone = document.getElementById('phoneNumber').value.trim();
        const email = document.getElementById('email').value.trim();
        const errorDiv = document.getElementById('contact-error');
        
        if (phone === '' && email === '') {
            event.preventDefault();
            errorDiv.style.display = 'block';
        } else {
            errorDiv.style.display = 'none';
        }
    });
</script>

<#elseif section = "info">
</#if>
</@layout.registrationLayout>
