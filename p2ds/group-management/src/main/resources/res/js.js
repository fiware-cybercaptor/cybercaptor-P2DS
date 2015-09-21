function toggle(eid) {
	var div = document.getElementById(eid);
	if(div.style.display == 'none' || div.style.display == '') {
		div.style.display = 'block';
    }
	else {
		div.style.display = 'none';
    }
}
