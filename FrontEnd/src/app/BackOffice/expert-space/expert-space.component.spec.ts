import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExpertSpaceComponent } from './expert-space.component';

describe('ExpertSpaceComponent', () => {
  let component: ExpertSpaceComponent;
  let fixture: ComponentFixture<ExpertSpaceComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExpertSpaceComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ExpertSpaceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
