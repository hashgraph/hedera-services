// the checkboxes that have the ability to hide things
var hiders = document.getElementsByClassName("hider");

// add a listener to each checkbox
for (var i = 0; i < hiders.length; i++) {
    hiders[i].addEventListener("click", function() {
        // the classes that exist on the checkbox that is clicked
        var checkboxClasses = this.classList;

        // the name of the class that should be hidden
        // each checkbox has 2 classes, "hider", and the name of the class to be hidden
        var toggleClass;
        for (j = 0; j < checkboxClasses.length; j++) {
            if (checkboxClasses[j] == "hider") {
                continue;
            }

            toggleClass = checkboxClasses[j];
            break;
        }

        // these are the objects on the page which match the class to toggle (discluding the input boxes)
        var matchingObjects = $("." + toggleClass).not("input");

        // go through each of the matching objects, and modify the hide count according to the value of the checkbox
        for (j = 0; j < matchingObjects.length; j++) {
            var currentHideCount = parseInt($(matchingObjects[j]).attr('data-hide')) || 0;

            var newHideCount;
            if ($(this).is(":checked")) {
                newHideCount = currentHideCount + 1;
            } else {
                newHideCount = currentHideCount - 1;
            }

            $(matchingObjects[j]).attr('data-hide', newHideCount);
        }
    });
}