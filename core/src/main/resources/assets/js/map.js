//RECUERDAD QUE LA LONGITUD Y LA LATITUD ESTAN ALREVEZ XD


mapboxgl.accessToken = 'pk.eyJ1IjoibmFuY3ltYXllayIsImEiOiJjbGh0dmdlamEyZ3FyM2RudTA5N2p6cjNwIn0.rI0dhokmQXjJhDpNC7DMNw';
navigator.geolocation.getCurrentPosition(showMap, showError);


  function showMap(position) {
    const latitude = position.coords.latitude;
    const longitude = position.coords.longitude;
    const latitudeDireccion = localStorage.getItem('dir-lat');
    const longitudeDireccion = localStorage.getItem('dir-long');
    console.log("LAT",latitudeDireccion);
    console.log("LONG",longitudeDireccion);
    const ubicacionActual = document.getElementById("ubicacion-actual");
    ubicacionActual.innerHTML="Actual: LONG: "+longitude+" & LAT: "+latitude;
    const map = new mapboxgl.Map({
      container: 'map',
      style: 'mapbox://styles/mapbox/streets-v12',
        center: [latitudeDireccion,longitudeDireccion],
        zoom: 13
    });

const ubicacion = new mapboxgl.Marker({ color: 'black', rotation: 0 })
.setLngLat([longitude, latitude])
.addTo(map);

const direccion = new mapboxgl.Marker({ color: 'black', rotation: 0 })
.setLngLat([latitudeDireccion,longitudeDireccion])
.addTo(map);



//RECUERDAD QUE LA LONGITUD Y LA LATITUD ESTAN ALREVEZ XD
fetch("https://fixfast-backend-production.up.railway.app/api/usuarios/workers")
    .then(response => response.json())
    .then(result => {
      console.log("THIS IS RESITJ",result.usuarios);
      const workerCards= document.getElementById("worker-profile-cards");
      for (let i = 0; i < result.total; i++) {
        console.log(i)
        const longitud=result.usuarios[i].latitud;
        const latitud=result.usuarios[i].longitud;
        const marker = new mapboxgl.Marker({ color: 'orange', rotation: 45 })
        .setLngLat([longitud, latitud])
        .addTo(map);

        //ADD WORKERS
        workerCards.innerHTML += `
        <!-- Single Advisor-->
        
        <div class="col-12 col-sm-6 col-lg-4">
          <button  id="${result.usuarios[i].uid}" onclick="handleButtonClick(this.id)" style="background-color: transparent; border-color: transparent;">
          <div class="single_advisor_profile wow fadeInUp" data-wow-delay="0.2s" style="visibility: visible; animation-delay: 0.2s; animation-name: fadeInUp;">
            <!-- Team Thumb-->
            <div class="advisor_thumb"><img src="${result.usuarios[i].img}" alt="">
              <!-- Social Info-->
              <div class="social-info"><a href="public_profile_worker.html"><i class="fa fa-star"></i><i class="fa fa-star"></i><i class="fa fa-star"></i><i class="fa fa-star"></i><i class="fa fa-star"></i></a></div>
             
              </div>
            <!-- Team Details-->
            <div class="single_advisor_details_info">
              <h6>${result.usuarios[i].nombre}</h6>
              <p class="designation">${result.usuarios[i].categoria}</p>
            </div>
          </div>
          </button>
        </div> `

      }
    })
    .catch(error => {
      // Handle any errors
      alert('An error occurred. Please try again later.');
      console.error('Error:', error);
    });
    
  }

  function showError(error) {
    switch (error.code) {
      case error.PERMISSION_DENIED:
        alert('User denied the request for Geolocation.');
        break;
      case error.POSITION_UNAVAILABLE:
        alert('Location information is unavailable.');
        break;
      case error.TIMEOUT:
        alert('The request to get user location timed out.');
        break;
      case error.UNKNOWN_ERROR:
        alert('An unknown error occurred.');
        break;
    }
  }

  //WHEN CLICK ON PROFILE CARDS
  function handleButtonClick(buttonId) {
    console.log('Button ID:', buttonId);//te da el worker id que clickeamos
    localStorage.setItem('uid-worker', buttonId);
    window.location.href = 'public_profile_worker.html';

  }


  
  //--------------------------------------------------------------------------------
  


  function setUpUserOnLoadPage(event) {
    console.log("event setUpUserOnLoadPage");
    event.preventDefault(); // Prevent form submission
    const uid = localStorage.getItem('uid');
  
    // Make the API request
    fetch("https://fixfast-backend-production.up.railway.app/api/buscar/usuarios/" + uid)
    .then(response => response.json())
    .then(result => {
      console.log(result);
      const ubicacionRegistrada = document.getElementById("ubicacion-registrada");
      const profilePictureImageSmall = document.getElementById('profilePictureImage-small');
      const userName= document.getElementById("name-user");
      const userCorreo= document.getElementById("correo-user");

      ubicacionRegistrada.innerHTML="Direccion: "+ result.results[0].direccion;
      userName.innerHTML=result.results[0].nombre;
      if(result.results[0].img){
        const newImageUrl = result.results[0].img; 
        profilePictureImageSmall.src = newImageUrl;
      }

      if(result.results[0].rol==="USER_ROLE")
      userCorreo.innerHTML="Usuario";
      else{
        userCorreo.innerHTML="Trabajador: "+result.results[0].categoria;
      }





    })
    .catch(error => {
      // Handle any errors
      alert('An error occurred. Please try again later.');
      console.error('Error:', error);
    });
  
    
  }
  
  window.addEventListener('load', setUpUserOnLoadPage);



  // // Create a default Marker, colored black, rotated 45 degrees.
// const CUBUAPmarker = new mapboxgl.Marker({ color: 'black', rotation: 0 })
// .setLngLat([-98.198800, 18.995600])
// .addTo(map);

// // Create a default Marker, colored black, rotated 45 degrees.
// const maker_worker_1 = new mapboxgl.Marker({ color: 'orange', rotation: 45 })
// .setLngLat([-98.194890, 19.003950])
// .addTo(map);

// const maker_worker_2= new mapboxgl.Marker({ color: 'orange', rotation: 45 })
// .setLngLat([-98.200200, 19.009700])
// .addTo(map);

// const maker_worker_3= new mapboxgl.Marker({ color: 'orange', rotation: 45 })
// .setLngLat([-98.192020, 18.994470])
// .addTo(map);

// const maker_worker_4= new mapboxgl.Marker({ color: 'orange', rotation: 45 })
// .setLngLat([-98.197550, 18.980600])
// .addTo(map);
    