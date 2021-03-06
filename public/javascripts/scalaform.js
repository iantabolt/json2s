$(document).ready(function() {
    var $scala = $("#scala-code");
    $scala.html(prettyPrintOne($scala.html()));
    $scala.addClass("prettyprinted");
    $scala.addClass("prettyprint");
    $scala.find(".pln, .typ").each(function() {
        $(this).data("curr", $(this).text());
    });
    $scala.find(".classname .pln, .classname .typ").each(function() {
        var $classname = $(this);
        $classname.editable({
            success: function(response, newValue) {
                console.log(newValue);
                var prevValue = $classname.data('curr');
                $scala.find(".pln, .typ").each(function() {
                    if ($(this).data("curr") == prevValue) {
                        $(this).data("curr", newValue);
                        $(this).text(newValue);
                    }
                });
            }
        });
    });
});
