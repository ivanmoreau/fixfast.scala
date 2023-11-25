navigator.geolocation.getCurrentPosition(showMap, showError);

function showMap(position) {
  const latitude = position.coords.latitude;
  const longitude = position.coords.longitude;


  const ubicacionActual = document.getElementById("ubicacion-actual");
  ubicacionActual.innerHTML="Actual: LONG: "+longitude+" & LAT: "+latitude;
}

function showError(error) {
  const ubicacionActual = document.getElementById("ubicacion-actual");
  ubicacionActual.innerHTML = "No se puede acceder a la ubicación actual."
}