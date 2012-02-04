
DOMSetup = function(element, container) {
    var el = $(element);
    if (container != null)
        el.appendTo(container);
    else
        el.appendTo('body');
}
 
DOMTearDown = function(selector) {
    $(selector).remove();
}