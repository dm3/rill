class SubsectionsController < ApplicationController

  before_action :set_course
  before_action :set_chapter
  before_action :set_section
  before_action :set_subsection, except: [:list, :index, :new, :create]
  before_action :set_breadcrumb, except: [:list, :index, :new, :create]

  def list
    @star = params[:star]
    @subsections = @section.subsections.find_by_star(@star)
    render partial: 'list'
  end

  def index
  end

  def show
  end

  def new
    @subsection = @section.subsections.build
  end

  def edit
  end

  def create
    @subsection = @section.subsections.build(subsection_params)
    if @subsection.save
      redirect_to chapter_sections_path(@section), notice: 'Subsection was successfully created.'
    else
      render :new
    end
  end

  def update
    if @subsection.update(subsection_params)
      redirect_to [@chapter, @section], notice: 'Subsection was successfully updated.'
    else
      render :edit
    end
  end

  def destroy
    @subsection.trash
    redirect_to [@chapter, @section], notice: 'Subsection was successfully destroyed.'
  end

  def activate
    @subsection.activate
    redirect_to [@chapter, @section]
  end

  def deactivate
    @subsection.deactivate
    redirect_to [@chapter, @section]
  end

  def moveup
    @subsection.move_higher
    redirect_to [@chapter, @section], notice: 'Subsection was successfully moved up.'
  end

  def movedown
    @subsection.move_lower
    redirect_to [@chapter, @section], notice: 'Subsection was successfully moved down.'
  end

private

  def set_breadcrumb
    set_crumb({name: @chapter.title, url: chapter_sections_path(@chapter)})
    set_crumb({name: @section.title, url: chapter_section_path(@chapter, @section)})
    set_crumb({name: @subsection.title, url: chapter_section_subsection_path(@chapter, @section,@subsection)})
  end

  def set_course
    @course = Course.current
  end

  def set_chapter
    @chapter = @course.chapters.find_by_uuid(params[:chapter_id])
  end

  def set_section
    @section = @chapter.sections.find_by_uuid(params[:section_id])
  end

  def set_subsection
    @subsection = @section.subsections.find_by_uuid(params[:id])
  end

  def subsection_params
    params.require(:subsection).permit(:title, :description, :stars)
  end

end