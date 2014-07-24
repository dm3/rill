bindAddButtons = ->
  $('.add-subsection').unbind()
  $('.add-subsection').bind 'click', (event) ->
    after = $(event.currentTarget).data('after')
    url = $(event.currentTarget).data('url')
    subsection_id = $(event.currentTarget).data('id')
    if subsection_id
      subsection_position = $("#subsections_" + subsection_id + "_position")[0].value
      position = parseInt(subsection_position) + 1
    else
      position = 0
    $.ajax url,
        type: 'POST'
        dataType: 'html'
        data: "position=" + position
        error: (jqXHR, textStatus, errorThrown) ->
          console.log "AJAX Error: #{ textStatus }"
        success: (data, textStatus, jqXHR) ->
          $('#' + after).after(data)
          bindAddButtons()
          bindDeleteButtons()
          updateViewPositions()
          refreshPreview()

bindDeleteButtons = ->
  $('.delete-subsection').unbind()
  $('.delete-subsection').bind 'click', (event) ->
    if confirm('Are you sure you want to delete this?')
      deleteItem = $(event.currentTarget).data('item')
      url = $(event.currentTarget).data('url')
      $.ajax url,
        type: 'DELETE'
        dataType: 'json'
        error: (jqXHR, textStatus, errorThrown) ->
          console.log "AJAX Error: #{ textStatus }"
        success: (data, textStatus, jqXHR) ->
          $('#' + deleteItem).remove()
          updateViewPositions()
          refreshPreview()

bindSaveButton = ->
  $('.save').unbind()
  $('.save').bind 'click', (event) ->
    save()

save = ->
  form  = $("#section-form")
  url = form.attr("action")
  $("#edit-time").html('<img src="/spinner.gif" alt="Wait" />')
  $.ajax url,
    type: 'POST'
    dataType: 'json'
    data: form.serialize()
    error: (jqXHR, textStatus, errorThrown) ->
      console.log "AJAX Error: #{ textStatus }"
    success: (data, textStatus, jqXHR) ->
      $("#edit-time").html("Saved on: " + data.updated_at)
      refreshPreview()

initializeAutoSave = ->
  setTimeout(autoSave, 10000)

autoSave = ->
  save()
  setTimeout(autoSave, 10000)

refreshPreview = ->
  $('#preview').attr("src", $('#preview').attr("src"))
  height = document.getElementById('preview').contentWindow.document.body.scrollHeight
  $('#preview').css('height', height)

updateViewPositions = ->
  $(".subsection-position").each (position) ->
    this.value = position

################################################################################

# on load run:
$ ->
  bindAddButtons()
  bindDeleteButtons()
  bindSaveButton()
  initializeAutoSave()

################################################################################
