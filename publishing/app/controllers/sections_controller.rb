class SectionsController < ApplicationController

  before_action :set_course
  before_action :set_chapter
  before_action :set_section, except: [:index, :new, :create]
  before_action :set_breadcrumb, except: [:index, :new, :create]

  def preview
    @subsections = @section.subsections.find_by_star(params[:star])
    render layout: 'preview'
  end

  def index
    redirect_to @chapter
  end

  def show
    @all_subsections = @section.subsections.group_by(&:stars)
  end

  def update
    if @section.update(section_params)
      @section.update_attribute :updated_at, Time.now
      redirect_to chapter_section_path(@chapter, @section), notice: 'Section was successfully updated.'
    else
      render :show
    end
  end

private

  def set_breadcrumb
    set_crumb({name: @chapter.title, url: chapter_sections_path(@chapter)})
    set_crumb({name: @section.title, url: chapter_section_path(@chapter, @section)})
  end

  def set_course
    @course = Course.current
  end

  def set_chapter
    @chapter = @course.chapters.find_by_uuid(params[:chapter_id])
  end

  def set_section
    @section = @chapter.sections.find_by_uuid(params[:id])
  end

  def section_params
    params.require(:section).permit! #(:title, :description,
    #   subsections: {star: []}
    # )
  end

end
