const signUpButton = document.getElementById("signUp");
const signInButton = document.getElementById("signIn");
const container = document.getElementById("container");

signUpButton.addEventListener("click", () => {
  container.classList.add("right-panel-active");
});

signInButton.addEventListener("click", () => {
  container.classList.remove("right-panel-active");
});


document.getElementById("registro-user").addEventListener("click", signUpUser);
document.getElementById("login-user").addEventListener("click", loginUser);

function signUpUser(event) {
  console.log("click registro-user");
  event.preventDefault(); // Prevent form submission

  const name = document.getElementById("nameInput").value;
  const email = document.getElementById("correoInput").value;
  const password = document.getElementById("passwordInput").value;
  const direccion = localStorage.getItem('direccion');
  console.log(direccion);
  const longitud =localStorage.getItem('longitude');
  const latitud = localStorage.getItem('latitude');
  console.log( latitud);
  console.log( longitud);
    
  // Create an object with the email and password
  const data = {
    nombre: name,
    direccion: direccion,
    longitud: longitud,
    latitud:latitud,
    correo: email,
    password: password,
    rol: "USER_ROLE",

  };


  // Make the API request
  fetch("https://fixfast-backend-production.up.railway.app/api/usuarios", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
  .then(response => response.json())
  .then(result => {
    // Check if the response indicates an error
    if (result.errors && result.errors.length > 0) {
      const errorMessage = result.errors[0].msg;
      alert(errorMessage);
    } else {
      // No error, sign-up was successful
      if (confirm("!USUARIO CREADO CORRECTAMENTE!   ¿QUIERES INICIAR SESION?")) {
        window.location.href = 'Inicia-sesion.html';
      } else {
        
      }
      console.log("USUARIO CREADO",result);
    
    }
  })
  .catch(error => {
    // Handle any errors
    alert('An error occurred. Please try again later.');
    console.error('Error:', error);
  });
}

function loginUser(event) {
  console.log("click login-user");
  event.preventDefault(); // Prevent form submission

  const email = document.getElementById("emailInput-login").value;
  const password = document.getElementById("passwordInput-login").value;

  // Create an object with the email and password
  const data = {
    correo: email,
    password: password
  };

  // Make the API request
  fetch("https://fixfast-backend-production.up.railway.app/api/auth/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
  .then(response => response.json())
  .then(result => {
    // Check if the response indicates an error
    const msg = result.msg;
    const uid = result.usuario.uid;
    const latitud = result.usuario.latitud;
    const longitud = result.usuario.longitud;
    console.log(latitud);
    if (msg) {
      alert(msg);
      if(msg === "Login ok"){
        localStorage.setItem("dir-lat", latitud);
        localStorage.setItem("dir-long",longitud);
        localStorage.setItem("uid", uid);
        window.location.href = 'index_login.html';
      }
    } 
    
  })
  .catch(error => {
    // Handle any errors
    alert('An error occurred. Please try again later.');
    console.error('Error:', error);
  });
}